package peloton.persistence

final case class EncodedState(payload: Array[Byte], revision: Long, timestamp: Long)
