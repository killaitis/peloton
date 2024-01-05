package peloton.persistence

/**
  * A version of [[DurableState]] where the payload is no longer typed, but encoded 
  * to an array of `Byte`.
  *
  * @param payload the encoded payloadan an `Array[Byte]`
  * @param revision the revision of the payload
  * @param timestamp the timestamp when this revision was written (in UNIX epoch milliseconds)
  */
final case class EncodedState(
  payload: Array[Byte], 
  revision: Long, 
  timestamp: Long
)
