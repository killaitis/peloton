package peloton.persistence

/**
  * A persistent actor event/message.
  *
  * @tparam A the type of the payload
  * @param payload the payload, i.e., the serialize actor message.
  * @param timestamp the timestamp when the event was written
  */
final case class Event[A](payload: A, timestamp: Long)
