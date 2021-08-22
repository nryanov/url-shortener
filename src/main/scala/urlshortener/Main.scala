package urlshortener

import zio._
import zio.clock.Clock
import zio.interop.catz._
import urlshortener.http.Route
import urlshortener.core.UrlShortener
import org.http4s.server.Router
import org.http4s.syntax.kleisli._
import org.http4s.blaze.server.BlazeServerBuilder
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

object Main extends zio.App {
  type Server[A] = RIO[Clock, A]

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (for {
      urlShortener <- ZIO.service[UrlShortener.Service]
      routes = Route(urlShortener)
      _ <- runServer(routes)
    } yield ()).provideCustomLayer(UrlShortener.live).orDie.exitCode

  def runServer(
    routes: Route
  ): ZIO[zio.ZEnv, Throwable, Unit] = {
    val yaml =
      OpenAPIDocsInterpreter().toOpenAPI(routes.endpoints, "Api", "1.0").toYaml

    ZIO.runtime[ZEnv].flatMap { implicit runtime =>
      BlazeServerBuilder[Server](runtime.platform.executor.asEC)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(
          Router(
            "/" -> routes.route,
            "/docs" -> new SwaggerHttp4s(yaml = yaml, contextPath = List("openapi"))
              .routes[ZIO[Has[Clock.Service], Throwable, *]]
          ).orNotFound
        )
        .serve
        .compile
        .drain
    }
  }
}
