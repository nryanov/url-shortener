package urlshortener.http

import zio._
import sttp.tapir.ztapir._
import sttp.model.{Header, StatusCode}
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import urlshortener.core.UrlShortener
import urlshortener.core.UrlShortener.UrlShortenerError
import urlshortener.core.types.{ShortUrl, Url}

final class Route(urlShortener: UrlShortener.Service) {
  private val baseEndpoint: ZEndpoint[Unit, (StatusCode, Error), Unit] =
    endpoint.errorOut(
      statusCode
        .example(StatusCode.NotFound)
        .and(jsonBody[Error].example(Error(code = 404, reason = "Url not found")))
    )

  private val createEndpoint: ZEndpoint[Url, (StatusCode, Error), (StatusCode, ShortUrl)] =
    baseEndpoint.post
      .in(stringBody.example("http://localhost:8080"))
      .out(statusCode.example(StatusCode.Ok).example(StatusCode.Created).and(stringBody))
      .description("Create new short url")
      .tag("api")

  private val createRoute =
    createEndpoint.serverLogic(url =>
      toRoute(urlShortener.encode(url).map {
        case (shortUrl, exist) if exist => (StatusCode.Ok, shortUrl)
        case (shortUrl, _)              => (StatusCode.Created, shortUrl)
      })
    )

  private val redirectEndpoint: ZEndpoint[String, (StatusCode, Error), (StatusCode, List[Header])] =
    baseEndpoint.get
      .in(path[String]("shortUrl"))
      .out(statusCode.example(StatusCode.Found).and(headers))
      .description("Use existing short url")
      .tag("api")

  private val redirectRoute =
    redirectEndpoint.serverLogic(shortUrl =>
      toRoute(
        urlShortener
          .original(shortUrl)
          .map(originalUrl => (StatusCode.Found, List(Header.location(originalUrl))))
      )
    )

  private def toRoute[A](
    fa: IO[UrlShortenerError, A]
  ): Task[Either[(StatusCode, Error), A]] =
    fa.map(Right(_)).catchSome {
      case err: UrlShortener.EncodingError => errorResponse[A](StatusCode.InternalServerError, err)
      case err: UrlShortener.NotFound      => errorResponse[A](StatusCode.NotFound, err)
    }

  private def errorResponse[A](
    statusCode: StatusCode,
    err: Throwable
  ): UIO[Either[(StatusCode, Error), A]] = UIO.left {
    (
      statusCode,
      Error(code = statusCode.code, reason = err.getLocalizedMessage)
    )
  }

  val endpoints = List(createEndpoint, redirectEndpoint)

  val route = ZHttp4sServerInterpreter().from(List(createRoute, redirectRoute)).toRoutes
}

object Route {
  def apply(urlShortener: UrlShortener.Service): Route = new Route(urlShortener)
}
