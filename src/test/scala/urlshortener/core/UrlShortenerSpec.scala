package urlshortener.core

import zio.test._
import zio.test.Assertion._
import urlshortener.core.UrlShortener.NotFound

object UrlShortenerSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("UrlShortener")(
      testM("encode url") {
        val url = "https://localhost:8080"
        for {
          shortUrl <- UrlShortener.encode(url)
          cachedUrl <- UrlShortener.original(shortUrl._1)
        } yield assert(shortUrl._1.length)(equalTo(10)) && assert(cachedUrl)(equalTo(url))
      },
      testM("do not overwrite an already existing short url") {
        val url = "https://localhost:8080"
        for {
          shortUrl1 <- UrlShortener.encode(url)
          shortUrl2 <- UrlShortener.encode(url)
        } yield assert(shortUrl1)(equalTo(shortUrl2))
      },
      testM("fail with NotFound error") {
        for {
          result <- UrlShortener.original("url").run
        } yield assert(result)(fails(equalTo(NotFound("url"))))
      }
    ).provideCustomLayerShared(UrlShortener.live.orDie)
}
