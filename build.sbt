ThisBuild / scalaVersion     := "2.13.5"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "nano"
ThisBuild / organizationName := "example"

val zioVersion = "1.0.4-2"
val doobieVersion = "0.9.2"

lazy val root = (project in file("."))
  .settings(
    name := "nano-backend",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.1.7",
      "org.tpolecat"                  %% "doobie-core"            % doobieVersion,
      "org.tpolecat"                  %% "doobie-hikari"          % doobieVersion,
      "org.tpolecat"                  %% "doobie-postgres"        % doobieVersion,
      "io.github.gaelrenoux"          %% "tranzactio"             % "1.2.0",
      "org.polynote"                  %% "uzhttp"                 % "0.2.6",
      "dev.zio"                       %% "zio"                    % zioVersion,
      "dev.zio"                       %% "zio-config"             % "1.0.0",
      "dev.zio"                       %% "zio-interop-cats"       % "2.3.1.0",
      "dev.zio"                       %% "zio-streams"            % zioVersion,
      "dev.zio"                       %% "zio-json"               % "0.1+18-466aeb0e-SNAPSHOT",
      "dev.zio"                       %% "zio-test"               % zioVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    resolvers      += Resolver.sonatypeRepo("snapshots"),
    dockerExposedPorts ++= Seq(9000),
    dockerUsername := Some("gurghet"),
    dockerRepository := Some("index.docker.io"),
    dockerGroupLayers in Docker := PartialFunction.empty,
    dockerBaseImage := "adoptopenjdk/openjdk11",
    version := "0.2.0"
  )
enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
//enablePlugins(GraalVMNativeImagePlugin)