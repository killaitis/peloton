package peloton.http

import cats.syntax.either.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.Duration

object Codecs:
  
  given Decoder[FiniteDuration] = Decoder.decodeLong.emap { dur => 
    Either.catchNonFatal {
      Duration.fromNanos(dur)
    }.leftMap(_ => "Decoder[FiniteDuration]")
  }

  given Encoder[FiniteDuration] with
    final def apply(dur: FiniteDuration): Json = Json.fromLong(dur.toNanos)

end Codecs