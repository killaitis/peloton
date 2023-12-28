package peloton.persistence

import peloton.utils.Kryo

import cats.effect.IO

/**
  * A [[PayloadCodec]] typeclass implementation for type `A` that uses Kryo encoding/decoding under the hood
  *
  */
class KryoPayloadCodec[A] extends PayloadCodec[A]:
  override def encode(payload: A): IO[Array[Byte]] = IO.fromTry(Kryo.serializer.serialize(payload))
    
  override def decode(bytes: Array[Byte]): IO[A] = IO.fromTry(Kryo.serializer.deserialize[A](bytes))

object KryoPayloadCodec:

  inline def create[A]: PayloadCodec[A] = 
    new KryoPayloadCodec[A]
