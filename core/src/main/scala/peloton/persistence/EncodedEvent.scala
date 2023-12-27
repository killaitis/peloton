package peloton.persistence

final case class EncodedEvent(
  payload: Array[Byte], 
  timestamp: Long,
  isSnapshot: Boolean
)
