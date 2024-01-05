package peloton.persistence

import cats.effect.IO

/**
  * The result of the [[EventHandler]]. 
  */
enum EventAction[+E]:
  case Ignore
  case Persist(event: E)

object EventAction:
  def ignore[E]: IO[EventAction[E]] = IO.pure(EventAction.Ignore)
  def persist[E](event: E): IO[EventAction[E]] = IO.pure(EventAction.Persist(event))
