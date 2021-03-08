import doobie.util.ExecutionContexts
import zio.{Task, ZIO, ZLayer, ZManaged, blocking, system}
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


  val dataSource: DataSource = {
    val mutableDataSource = new HikariDataSource
//    mutableDataSource.setJdbcUrl("jdbc:postgresql://postgres-nano-postgresql:5432/postgres")
    mutableDataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres")
    mutableDataSource.setUsername("postgres")
//    mutableDataSource.setPassword("t6KDX2XQJS")
    mutableDataSource.setPassword("example")
    mutableDataSource.setMaximumPoolSize(8)
    mutableDataSource.setConnectionTimeout(1000)
    mutableDataSource
  }
}
