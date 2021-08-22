package urlshortener.core

import java.security.SecureRandom

import urlshortener.core.types._
import zio._
import zio.macros._
import zio.stm._

@accessible
object UrlShortener {
  type UrlShortener = Has[Service]

  private val alphabet: String = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_"
  private val length: Int = 10

  trait Service {
    def encode(url: Url): IO[UrlShortenerError, (ShortUrl, Boolean)]

    def original(shortenUrl: ShortUrl): IO[UrlShortenerError, Url]
  }

  val live: ZLayer[Any, UrlShortenerError, UrlShortener] =
    (for {
      secureRandom <- Task.effect {
        val sr = new SecureRandom()
        sr.setSeed(123456789L)
        sr
      }
      to <- TMap.empty[Url, ShortUrl].commit
      from <- TMap.empty[ShortUrl, Url].commit
    } yield Live(secureRandom, to, from)).orDie.toLayer

  sealed abstract class UrlShortenerError(msg: String, cause: Option[Throwable])
      extends Exception(msg, cause.orNull)

  final case class EncodingError(url: Url, cause: Throwable)
      extends UrlShortenerError(s"Error happened while encoding url: $url", Some(cause))

  final case class NotFound(shortUrl: ShortUrl)
      extends UrlShortenerError(s"Original url for $shortUrl was not found", None)

  private case class Live(random: SecureRandom, to: TMap[Url, ShortUrl], from: TMap[ShortUrl, Url])
      extends Service {
    override def encode(url: Url): IO[UrlShortenerError, (ShortUrl, Boolean)] =
      encode0(url).flatMap(updateInMemoryCache(url, _).commit)

    // update in memory cache in transaction to be sure that we do not overwrite an already existing shorten url.
    // if N (>1) threads will try to update cache then only one will succeed while others get a cached value.
    def updateInMemoryCache(url: Url, shortUrl: ShortUrl): ZSTM[Any, Nothing, (ShortUrl, Boolean)] =
      for {
        _ <- to.putIfAbsent(url, shortUrl)
        _ <- from.putIfAbsent(shortUrl, url)
        value <- to.get(url).flatMap {
          // created new record
          case Some(cached) if cached == shortUrl => STM.succeed((cached, false))
          // use existing record
          case Some(cached) => STM.succeed((cached, true))
          case None         => STM.die(new IllegalStateException("Cached value was accidentally deleted"))
        }
      } yield value

    override def original(shortenUrl: Url): IO[UrlShortenerError, Url] =
      from.get(shortenUrl).commit.flatMap(ZIO.fromOption(_)).orElseFail(NotFound(shortenUrl))

    private def encode0(url: Url): IO[EncodingError, String] = IO.effect {
      val sb = new StringBuilder(length)
      (0 until length).foreach(_ => sb.append(alphabet.charAt(random.nextInt(alphabet.length()))))
      sb.toString()
    }.mapError(EncodingError(url, _))
  }
}
