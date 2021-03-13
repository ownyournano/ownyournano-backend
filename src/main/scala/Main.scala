import BinanceHoldingPoint.jsonEncoder
import _root_.doobie.implicits._
import _root_.doobie.implicits.javasql._
import _root_.doobie.implicits.javatime._
import doobie.Transactor
import io.github.gaelrenoux.tranzactio._
import io.github.gaelrenoux.tranzactio.doobie.{Connection, Database, tzio}
import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient, send}
import sttp.client3.{Request, UriContext, basicRequest}
import uzhttp.server.Server
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.config.ReadError
import zio.duration.durationInt
import zio.json._

import java.net.InetSocketAddress
import java.sql.Timestamp
import java.time.{Instant, LocalDateTime, ZoneOffset}

case class NanoChartsResponse(timestamp: Long, binanceHoldings: String)
object NanoChartsResponse {
  implicit val decoder: JsonDecoder[NanoChartsResponse] = DeriveJsonDecoder.gen[NanoChartsResponse]
}
case class BinanceHoldingPoint(createdAt: Timestamp, amount: String)
object BinanceHoldingPoint {
  implicit val jsonTimestampEncoder: JsonEncoder[Timestamp] =
    JsonEncoder.instant.contramap[Timestamp](timestamp => Instant.ofEpochMilli(timestamp.getTime))
  implicit val jsonEncoder: JsonEncoder[BinanceHoldingPoint] = DeriveJsonEncoder.gen[BinanceHoldingPoint]
}

object Main extends App {
  final val available_supply = BigInt("133246498023723284791374643157344384749")

  final private val request: Request[Either[String, String], Any] =
    basicRequest.get(uri"https://nanocharts.info/data/nanoRepCharts.json")
  final private val sent: ZIO[SttpClient, Throwable, sttp.client3.Response[Either[String, String]]] = send(
    request
  )

  final private val fetchBinanceHoldings: ZIO[SttpClient, Serializable, BigInt] = for {
    either <- sent
    response = either.body match {
      case Left(errorMessage) => ZIO.fail(error = s"Error during request: $errorMessage")
      case Right(value) =>
        value.fromJson[NanoChartsResponse] match {
          case Left(decodingError) => ZIO.fail(error = s"Error during decoding $decodingError")
          case Right(value) =>
            IO(BigInt(value.binanceHoldings))
              .mapError(e => s"Error during conversion to bigInt ${e.getLocalizedMessage}")
        }
    }
    binanceHoldings <- response
  } yield binanceHoldings

  final private def insertCurrentBinanceHolding(
    amount: BigInt
  ): ZIO[Database with Clock, DbException, Int] = {
    def insertCurrentBinanceHoldingQuery(
      now: Instant,
      amount: String
    ): ZIO[Has[Transactor[Task]], DbException, Int] = {
      val timestamp = new Timestamp(now.toEpochMilli)
      tzio {
        sql"INSERT INTO binance_holdings (datapoint_id, created_at, amount) VALUES (${timestamp.getTime / 1000}, $timestamp, $amount)".update.run
      }
    }

    clock.instant.flatMap { now =>
      Database.transactionOrDie(insertCurrentBinanceHoldingQuery(now, amount.toString))
    }
  }

  final private val fetchBinanceHoldingHistory: ZIO[Database, DbException, List[BinanceHoldingPoint]] = {
    val queryBinanceHoldingHistory: ZIO[Connection, DbException, List[BinanceHoldingPoint]] = {
      val transaction = tzio {
        sql"SELECT created_at, amount FROM binance_holdings".query[BinanceHoldingPoint].to[List]
      }
      transaction
    }
    Database.transactionOrDie(queryBinanceHoldingHistory)
  }

  // every 7 minutes fetch binance holdings
  final private val fetchBinanceAndSaveLoop: ZIO[Database with Clock with SttpClient, Serializable, Nothing] = {
    val wait7minutes: URIO[Clock, Unit] = clock.sleep(7.minutes)
    val fetchBinanceThenSaveThenWait7minutes: ZIO[Database with Clock with SttpClient, Serializable, Unit] =
      for {
        amount <- fetchBinanceHoldings
        _      <- insertCurrentBinanceHolding(amount)
        _      <- wait7minutes
      } yield ()
    fetchBinanceThenSaveThenWait7minutes.forever
  }
  // create a new entry in the database with the new amount
  // at the same time
  // run a webserver, when queried gives the timeseries of amounts
  final private val server: URIO[Has[Config] with Database with Blocking with Clock, Nothing] =
    ZIO.service[Config].flatMap { cfg =>
      Server
        .builder(new InetSocketAddress("0.0.0.0", cfg.httpPort))
        .handleSome {
          case req if req.uri.getPath == "/" =>
            //noinspection SimplifyBimapInspection
            fetchBinanceHoldingHistory
              .map(listOfPoints => computeDailyLast(listOfPoints))
              .map(dailyLasts => dailyLasts.sortBy(_.createdAt))
              .map(orderedDailyLasts => orderedDailyLasts.toJson)
              .map(jsonArray => uzhttp.Response.plain(jsonArray, headers = jsonWithCorsHeaders))
              .mapError(
                e => uzhttp.HTTPError.InternalServerError(s"unexpected error: ${e.getLocalizedMessage}")
              )
        }
        .serve
        .useForever
        .orDie
    }

  private final val jsonWithCorsHeaders: List[(String, String)] = List(
    "Access-Control-Allow-Origin" -> "*",
    "Content-Type" -> "application/json; charset=utf-8"
  )

  final private def computeDailyLast(listOfPoints: List[BinanceHoldingPoint]) =
    listOfPoints
      .groupBy(binanceHoldingPoint => {
        val instant = Instant.ofEpochMilli(binanceHoldingPoint.createdAt.getTime)
        val date    = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
        date.toLocalDate
      })
      .map(entry => entry._2.last)
      .toList

  val databaseLayer: ZLayer[ZEnv, ReadError[String], Database] = (ZLayer
    .requires[ZEnv] >+> (Config.live >>> DatabaseDataSource.dataSourceLayer)) >>> Database.fromDatasource

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (server <&> fetchBinanceAndSaveLoop)
      .provideCustomLayer(databaseLayer ++ HttpClientZioBackend.layer() ++ Config.live)
      .exitCode
}
