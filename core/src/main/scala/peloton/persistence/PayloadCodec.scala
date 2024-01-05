package peloton.persistence

import cats.effect.IO


/**
  * An encoder and decoder type class for a given payload type `A`
  */
trait PayloadCodec[A]:
  /**
    * Encodes (serializes) a given payload of type `A` to a raw byte array.
    * 
    * This method is effectful (because encoding could fail) and is therefore wrapped in an `IO` context.
    *
    * @param payload 
    *   The payload of type `A`
    * @return 
    *   An `IO` effect that returns an `Array[Byte]` on success
    */
  def encode(payload: A): IO[Array[Byte]]

  /**
    * Decodes (deserializes) a payload of type `A` from a given byte array
    *
    * This method is effectful (because decoding could fail) and is therefore wrapped in an `IO` context.
    * 
    * @param bytes 
    *   A raw byte array
    * @return
    *   An `IO` effect that returns the decoded payload of type `A` on success
    */
  def decode(bytes: Array[Byte]): IO[A]
