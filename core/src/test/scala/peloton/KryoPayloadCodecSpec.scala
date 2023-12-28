package peloton

import peloton.persistence.PayloadCodec

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import cats.effect.testing.scalatest.AsyncIOSpec

class KryoPayloadCodecSpec 
    extends AsyncFlatSpec 
      with AsyncIOSpec 
      with Matchers:

  behavior of "A KryoPayloadCodec"
  
  import KryoPayloadCodecSpec.*

  it should "encode instances of a given type" in:
    val payload = MyData(i = 13, 
                         s = "gee", 
                         b = true, 
                         l = List("foo", "bar"), 
                         o = Some(4711L)
                        )
    payloadCodec
      .encode(payload)
      .assertNoException

  it should "encode instances of a given type with missing optional fields" in:
    val payload = MyData(i = 33, 
                         s = "Scala", 
                         b = false,
                         l = List(),
                         o = None
                        )
    payloadCodec
      .encode(payload)
      .assertNoException

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

end KryoPayloadCodecSpec

object KryoPayloadCodecSpec:
  final case class MyData(i: Int, 
                          s: String, 
                          b: Boolean, 
                          l: List[String], 
                          o: Option[Long]
                        )

  val payloadCodec: PayloadCodec[MyData] = persistence.KryoPayloadCodec.create

end KryoPayloadCodecSpec
