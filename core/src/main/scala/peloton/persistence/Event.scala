package peloton.persistence

/**
  * A container to store the events of an event sourced actor as its payload.
  *
  * @tparam A the type of the payload
  * @param payload the typed (unencoded) payload
  * @param timestamp the timestamp when the event was written
  */
final case class Event[A](
  payload: A,
  timestamp: Long
)
