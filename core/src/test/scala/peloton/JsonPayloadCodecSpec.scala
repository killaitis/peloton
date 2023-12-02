package peloton

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import cats.effect.testing.scalatest.AsyncIOSpec

import io.circe.*
import io.circe.generic.semiauto.*
import persistence.PayloadCodec

class JsonPayloadCodecSpec 
    extends AsyncFlatSpec 
      with AsyncIOSpec 
      with Matchers:

  behavior of "A JsonPayloadCodec"
  
  import JsonPayloadCodecSpec.*

  it should "encode instances of a given type" in:
    val payload = MyData(i = 13, 
                         s = "gee", 
                         b = true, 
                         l = List("foo", "bar"), 
                         o = Some(4711L)
                        )
    payloadCodec
      .encode(payload)
      .asserting: encodedPayload => 
        String(encodedPayload) shouldBe """{"i":13,"s":"gee","b":true,"l":["foo","bar"],"o":4711}"""

  it should "encode instances of a given type with missing optional fields" in:
    val payload = MyData(i = 33, 
                         s = "Scala", 
                         b = false,
                         l = List(),
                         o = None
                        )
    payloadCodec
      .encode(payload)
      .asserting: encodedPayload => 
        String(encodedPayload) shouldBe """{"i":33,"s":"Scala","b":false,"l":[]}"""

  it should "decode a byte array to an instance of a given type" in:
    val payload = MyData(i = 5, 
                         s = "The Quick Brown Fox Jumps Over The Lazy Dog", 
                         b = false, 
                         l = List("😍", "😻", "💃"), 
                         o = Some(1234567890123456L))
    val decodedPayload = 
      for
        encodedPayload <- payloadCodec.encode(payload)
        decodedPayload <- payloadCodec.decode(encodedPayload)
      yield decodedPayload

    decodedPayload.asserting(_ shouldBe payload)

end JsonPayloadCodecSpec

object JsonPayloadCodecSpec:
  final case class MyData(i: Int, 
                          s: String, 
                          b: Boolean, 
                          l: List[String], 
                          o: Option[Long]
                        )

  val payloadCodec: PayloadCodec[MyData] = persistence.JsonPayloadCodec.create

end JsonPayloadCodecSpec
