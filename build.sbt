import com.typesafe.sbt.packager.docker._

lazy val kindProjectorVersion = "0.13.0"
lazy val zioVersion = "1.0.10"
lazy val zioLoggingVersion = "0.5.11"
lazy val tapirVersion = "0.18.3"
lazy val logbackVersion = "1.2.5"

lazy val compilerOptions: Seq[String] = Seq(
  "-deprecation",
  "-unchecked",
  "-encoding",
  "UTF-8",
  "-explaintypes",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:existentials",
  "-language:postfixOps",
  "-Ywarn-dead-code",
  "-Xlint",
  "-Xlint:constant",
  "-Xlint:inaccessible",
  "-Xlint:nullary-unit",
  "-Xlint:type-parameter-shadow",
  "-Xlint:_,-byname-implicit",
  "-Ymacro-annotations",
  "-Wdead-code",
  "-Wnumeric-widen",
  "-Wunused:explicits",
  "-Wunused:implicits",
  "-Wunused:imports",
  "-Wunused:locals",
  "-Wunused:patvars",
  "-Wunused:privates",
  "-Wvalue-discard",
  "-Xlint:deprecation",
  "-Xlint:eta-sam",
  "-Xlint:eta-zero",
  "-Xlint:implicit-not-found",
  "-Xlint:infer-any",
  "-Xlint:nonlocal-return",
  "-Xlint:unused",
  "-Xlint:valpattern"
)

lazy val root =
  project
    .in(file("."))
    .enablePlugins(JavaAppPackaging, DockerPlugin)
    .settings(
      name := "url-shortener",
      version := "0.1",
      scalaVersion := "2.13.6",
      scalacOptions ++= compilerOptions,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio" % zioVersion,
        "dev.zio" %% "zio-streams" % zioVersion,
        "dev.zio" %% "zio-macros" % zioVersion,
        "dev.zio" %% "zio-logging-slf4j" % zioLoggingVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-zio-http4s-server" % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s" % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
        "ch.qos.logback" % "logback-classic" % logbackVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % tapirVersion,
        "dev.zio" %% "zio-test" % zioVersion % Test,
        "dev.zio" %% "zio-test-sbt" % zioVersion % Test
      ),
      dependencyOverrides ++= Seq(
        "dev.zio" %% "zio-test" % zioVersion % Test
      ),
      addCompilerPlugin(
        ("org.typelevel" %% "kind-projector" % kindProjectorVersion).cross(CrossVersion.full)
      ),
      Test / parallelExecution := false,
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )
    .settings(
      dockerBaseImage := "openjdk:8-jre-alpine",
      dockerRepository := Some("index.docker.io"),
      dockerUsername := Some("nryanov"),
      dockerUpdateLatest := true,
      dockerExposedPorts := Seq(8080),
      Docker / packageName := "url-shortener",
      Docker / version := "latest",
      Docker / daemonUser := "daemon",
      dockerCommands ++= Seq(
        Cmd("USER", "root"),
        Cmd("RUN", "apk", "add", "--no-cache", "bash"),
        Cmd("USER", (Docker / daemonUser).value)
      )
    )
