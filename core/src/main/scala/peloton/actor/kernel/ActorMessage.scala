package peloton.actor.kernel

import cats.effect.Deferred
import cats.effect.IO

/**
  * Type of the response channel to transport a possible actor response back to the caller. 
  * 
  * We use a [[Deferred]] here where the consumer (the client) will listen and wait while the 
  * producer (the actor) will at some point in time send a response. The response is
  * either the actor's response or an error (`Throwable`). The `Deferred` 
  * is finally wrapped into an `Option`. This allows it to skip the whole creation of a
  * `Deferred` when no response is needed (`tell()`) and only allocate it if needed (`ask()`)
  */
private [kernel] type ActorResponseChannel = Deferred[IO, Either[Throwable, Any]]

/**
  * Type of the elements of the internal message queues. Each element consists of a pair of 
  *   - the incoming raw message sent to the actor of type `M`
  *   - an optional response channel the actor can use to send responses back to the caller (ASK pattern)
  */
private [kernel] type ActorMessage[M] = (M, Option[ActorResponseChannel])
