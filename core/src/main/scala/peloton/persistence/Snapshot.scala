package peloton.persistence

/**
  * A snapshot of a persistent actor's state.
  *
  * @tparam A the type of the payload
  * @param payload the payload, i.e., the serialize actor message.
  * @param timestamp the timestamp (UNIX epoch milliseconds) when the event was written
  */
final case class Snapshot[A](
  payload: A,
  timestamp: Long
)
