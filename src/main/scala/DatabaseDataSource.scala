import doobie.util.ExecutionContexts
import zio.{ Has, Task, ZIO, ZLayer, ZManaged, blocking, system }
import zio.blocking.Blocking
import zio.interop.catz._
import doobie._
import doobie.implicits._
import cats.effect._
import doobie.hikari._
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource

import java.sql.SQLException
import javax.sql.DataSource

object Transactor {
  val hikariLayer = for {
    cfg                     <- Config.configFromEnv.toManaged_
    connectExecutionContext <- ZIO.descriptor.map(descFiber => descFiber.executor.asEC).toManaged_
    blockingExecutionContext <- blocking
      .blocking(ZIO.descriptor.map(descFiber => descFiber.executor.asEC))
      .toManaged_
    hikariTransactor <- HikariTransactor
      .newHikariTransactor[Task](
        driverClassName = "org.postgresql.Driver",
        url = cfg.databaseUri,
        user = cfg.databaseUsername,
        pass = cfg.databasePassword,
        connectEC = connectExecutionContext,
        blocker = Blocker.liftExecutionContext(blockingExecutionContext)
      )
      .toManagedZIO
      .refineToOrDie[SQLException]
  } yield hikariTransactor
}

object DatabaseDataSource {

  val dataSourceLayer: ZLayer[Has[Config], Nothing, Has[DataSource]] = ZIO.service[Config].map { cfg =>
    val mutableDataSource = new HikariDataSource
//    mutableDataSource.setJdbcUrl("jdbc:postgresql://postgres-nano-postgresql:5432/postgres")
    mutableDataSource.setJdbcUrl(cfg.databaseUri)
    mutableDataSource.setUsername(cfg.databaseUsername)
//    mutableDataSource.setPassword("t6KDX2XQJS")
    mutableDataSource.setPassword(cfg.databasePassword)
    mutableDataSource.setMaximumPoolSize(8)
    mutableDataSource.setConnectionTimeout(1000)
    mutableDataSource
  }.toLayer
}
