import sttp.client3.{ Request, UriContext, basicRequest }
import sttp.client3.httpclient.zio.{ HttpClientZioBackend, SttpClient, send }
import zio._
import zio.json._
import doobie.Transactor
import io.github.gaelrenoux.tranzactio._
import io.github.gaelrenoux.tranzactio.doobie.{ Connection, Database, tzio }
import uzhttp.server.Server
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import _root_.doobie.implicits._
import _root_.doobie.implicits.javatime._
import _root_.doobie.implicits.javasql._

import java.net.InetSocketAddress
import java.sql.Timestamp
import java.time.Instant

case class NanoChartsResponse(timestamp: Long, binanceHoldings: String)
object NanoChartsResponse {
  implicit val decoder: JsonDecoder[NanoChartsResponse] = DeriveJsonDecoder.gen[NanoChartsResponse]
}
case class BinanceHoldingPoint(createdAt: Timestamp, amount: String)
object BinanceHoldingPoint {
  // apply does decoding?

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
      val timestamp = new Timestamp(now.getEpochSecond)
      tzio {
        sql"INSERT INTO binance_holdings (datapoint_id, created_at, amount) VALUES (${timestamp.getNanos}, $timestamp, $amount)".update.run
      }
    }

    clock.nanoTime.map(nanoTime => Instant.ofEpochMilli(nanoTime / 1000000)).flatMap { now =>
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
  final private val server: URIO[Database with Blocking with Clock, Nothing] = Server
    .builder(new InetSocketAddress("127.0.0.1", 5480))
    .handleSome {
      case req if req.uri.getPath == "/" =>
        //noinspection SimplifyBimapInspection
        fetchBinanceHoldingHistory
          .map(_.mkString)
          .map(uzhttp.Response.plain(_))
          .mapError(e => uzhttp.HTTPError.InternalServerError(s"unexpected error: ${e.getLocalizedMessage}"))
    }
    .serve
    .useForever
    .orDie

  val databaseLayer: ZLayer[ZEnv, Nothing, Database] = (ZLayer
    .requires[ZEnv] ++ ZLayer.succeed(DatabaseDataSource.dataSource)) >>> Database.fromDatasource

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (server <&> fetchBinanceAndSaveLoop)
      .provideCustomLayer(databaseLayer ++ HttpClientZioBackend.layer())
      .exitCode
}
