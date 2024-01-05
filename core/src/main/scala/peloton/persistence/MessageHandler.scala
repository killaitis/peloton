package peloton.persistence

import peloton.actor.ActorContext

import cats.effect.IO


/**
  * The message handler is applied to the current state and each incoming message of an 
  * event sourced actor and returns an [[EventAction]]. the event action defines how to 
  * deal with the message, e.g., just ignore it or create an event.
  */
type MessageHandler[S, M, E] = (state: S, message: M, context: ActorContext[S, M]) => IO[EventAction[E]]
