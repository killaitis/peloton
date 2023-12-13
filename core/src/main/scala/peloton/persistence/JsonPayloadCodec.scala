package peloton.persistence

import cats.effect.IO

import io.circe.*
import io.circe.syntax.*
import io.circe.parser
import io.circe.generic.semiauto.*

import java.nio.charset.StandardCharsets
import scala.deriving.Mirror

/**
  * A [[PayloadCodec]] typeclass implementation for type `A` that uses Json encoding/decoding under the hood
  *
  * @param codec 
  *   A given Circe Json `Codec` for type `A`
  */
class JsonPayloadCodec[A](using Codec[A]) extends PayloadCodec[A]:
  override def encode(payload: A): IO[Array[Byte]] = IO(payload.asJson.printWith(JsonPayloadCodec.jsonPrinter).getBytes())
  override def decode(bytes: Array[Byte]): IO[A] = IO.fromEither(parser.decode[A](new String(bytes, StandardCharsets.UTF_8)))

object JsonPayloadCodec:
  // The Json printer to encode the payload. We want a minimal output size, so we drop 
  // null values and disable indenting.
  private val jsonPrinter = Printer(dropNullValues = true, indent = "")

  inline def create[A](using inline A: Mirror.Of[A]): PayloadCodec[A] = 
    given Codec[A] = deriveCodec
    new JsonPayloadCodec[A]
