package http

import zio._
import zio.test._
import zio.clock.Clock
import zio.interop.catz._
import zio.test.Assertion._
import org.http4s._
import io.circe.Decoder
import io.circe.parser._
import io.circe.generic.auto._
import org.typelevel.ci.CIString
import urlshortener.http.{Error, Route}
import urlshortener.core.UrlShortener
import urlshortener.core.UrlShortener.{NotFound, UrlShortener}

object RouteSpec extends DefaultRunnableSpec {
  val runtime = zio.Runtime.default
  type ResultT[A] = ZIO[Any with Clock, Throwable, A]

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = suite("Route")(
    testM("shorten url") {
      for {
        routes <- ZIO.service[Route]
        request = Request[ResultT](method = Method.POST, uri = Uri(path = Uri.Path.Root))
          .withEntity("http://localhost:8080")
        response = routes.route.run(request).value
      } yield check(response, Status.Created)
    },
    testM("do not recreate already existing shorten url") {
      for {
        _ <- UrlShortener.encode("http://localhost:8080")
        routes <- ZIO.service[Route]
        request = Request[ResultT](method = Method.POST, uri = Uri(path = Uri.Path.Root))
          .withEntity("http://localhost:8080")
        response = routes.route.run(request).value
      } yield check(response, Status.Ok)
    },
    testM("return NotFound error") {
      for {
        routes <- ZIO.service[Route]
        request = Request[ResultT](
          method = Method.GET,
          uri = Uri(path = Uri.Path.Root).withPath(Uri.Path.unsafeFromString("url"))
        )
        response = routes.route.run(request).value
      } yield check(
        response,
        Status.NotFound,
        Error(code = 404, reason = NotFound("url").getLocalizedMessage)
      )
    },
    testM("return redirect") {
      for {
        encoded <- UrlShortener.encode("url")
        routes <- ZIO.service[Route]
        redirectRequest = Request[ResultT](
          method = Method.GET,
          uri = Uri(path = Uri.Path.Root).withPath(Uri.Path.unsafeFromString(encoded._1))
        )
        redirectResponse = routes.route.run(redirectRequest).value
      } yield check(
        redirectResponse,
        Status.Found,
        Header.Raw(CIString("Location"), "url")
      )
    }
  ).provideCustomLayer(createEnv)

  def check[A](
    actualResp: ResultT[Option[Response[ResultT[*]]]],
    expectedStatus: Status,
    expectedBody: A
  )(implicit
    decoder: Decoder[A]
  ) = {
    val response = runtime.unsafeRun(actualResp).get
    val status = response.status
    val body = response.body.compile.toVector.map(x => x.map(_.toChar).mkString(""))
    val data: Option[A] = decode[A](runtime.unsafeRun(body)).toOption

    assert(data)(isSome(equalTo(expectedBody))) && assert(expectedStatus)(equalTo(status))
  }

  def check(
    actualResp: ResultT[Option[Response[ResultT[*]]]],
    expectedStatus: Status,
    header: Header.Raw
  ) = {
    val response = runtime.unsafeRun(actualResp).get
    val status = response.status

    assert(expectedStatus)(equalTo(status)) && assert(response.headers.headers)(contains(header))
  }

  def check(
    actualResp: ResultT[Option[Response[ResultT[*]]]],
    expectedStatus: Status
  ): TestResult = {
    val response = runtime.unsafeRun(actualResp).get

    assert(expectedStatus)(equalTo(response.status))
  }

  val createEnv: ZLayer[Any, Nothing, UrlShortener with Has[Route]] = (UrlShortener.live >+>
    (for {
      service <- ZIO.service[UrlShortener.Service]
    } yield Route(service)).toLayer).orDie

}
