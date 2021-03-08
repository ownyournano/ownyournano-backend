import zio.config._
import ConfigDescriptor._
import zio.ZIO.descriptor
import zio.{ ZIO, system }
import zio.config._
import zio.system.System

final case class Config(databaseUri: String, databaseUsername: String, databasePassword: String)
object Config {
  final private val config: _root_.zio.config.ConfigDescriptor[Config] =
    (string(path = "DB_URI") |@| string(path = "DB_USER") |@| string(path = "DB_PWD"))(
      Config.apply,
      Config.unapply
    )

  val configFromEnv: ZIO[System, ReadError[String], Config] = for {
    env <- ConfigSource.fromSystemEnv
    configDescriptor = config from env
    config <- ZIO.fromEither(read(configDescriptor))
  } yield config
}
