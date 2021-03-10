import com.zaxxer.hikari.HikariDataSource
import zio.{Has, ZIO, ZLayer}

import javax.sql.DataSource

object DatabaseDataSource {

  val dataSourceLayer: ZLayer[Has[Config], Nothing, Has[DataSource]] = ZIO.service[Config].map { cfg =>
    val mutableDataSource = new HikariDataSource
    mutableDataSource.setJdbcUrl(cfg.databaseUri)
    mutableDataSource.setUsername(cfg.databaseUsername)
    mutableDataSource.setPassword(cfg.databasePassword)
    mutableDataSource.setMaximumPoolSize(8)
    mutableDataSource.setConnectionTimeout(1000)
    mutableDataSource
  }.toLayer
}
