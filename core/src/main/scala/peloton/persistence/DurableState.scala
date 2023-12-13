package peloton.persistence

/**
  * The durable (i.e. persistent) state of an actor.
  *
  * @tparam A the type of the payload
  * @param payload the payload, i.e., the state of an actor that os to be persisted.
  * @param revision the revision of the payload
  * @param timestamp the timestamp when this revision was written
  */
final case class DurableState[A](payload: A, revision: Long, timestamp: Long)
