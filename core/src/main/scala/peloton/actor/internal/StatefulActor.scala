package peloton.actor.internal

import peloton.actor.Actor
import peloton.actor.Actor.CanAsk
import peloton.actor.ActorContext
import peloton.actor.Behavior

import cats.effect.*
import cats.effect.std.Queue
import cats.effect.std.Mutex
import cats.implicits.*

import scala.concurrent.duration.FiniteDuration

private [actor] object StatefulActor:

  /**
    * Spawn a new [[Actor]] with simple, stateful behavior. 
    * 
    * Stateful actors maintain a mutable state which is passed to the message handler and can be updated using
    * the context's [[Context.setState]] method. The state is kept only in memory and is not persisted in 
    * any way.
    *
    * @param initialState 
    *   The initial state for the actor.
    * @param initialBehavior 
    *   The initial behavior, i.e., the message handler function. The function takes the current state of the actor, 
    *   the input (message) and the actor's [[Context]] as parameters and returns a new [[Behavior]], depending on 
    *   the state and the message. The behavior is evaluated effectful.
    * @return 
    *   An effect that creates a new [[Actor]]
    */
  def spawn[S, M](
    initialState: S,
    initialBehavior: Behavior[S, M]
  ): IO[Actor[M]] =
    for
      // Wrap the current behavior into a Ref
      behaviorRef  <- Ref.of[IO, Behavior[S, M]](initialBehavior)

      // Wrap the state into a Ref, so both the client (producer) and the message handler loop (consumer) can access it thread-safely.
      stateRef     <- Ref.of[IO, S](initialState)

      // Create the message queues for both the inbox and the stash. 
      inbox        <- Queue.unbounded[IO, ActorMessage[M]]
      stashed      <- Queue.unbounded[IO, ActorMessage[M]]

      // Use a Mutex to guard all access to both inbox and stash, i.e., make access atomic. This is neccessary to ensure that 
      // messages are always enquened in orrect order while moving messages from stash to inbox or vice versa.
      queueMutex   <- Mutex[IO]

      // Create the message processing loop and spawn it in the background (fiber)
      msgLoopFib   <- (for                        
                        (message, responseChannel)    
                                             <- inbox.take

                        state                <- stateRef.get

                        currentBehavior      <- behaviorRef.get

                        context               = new ActorContext[S, M](currentBehavior = currentBehavior):
                                                  override def tellSelf(message: M) =
                                                    queueMutex.lock.surround:
                                                      inbox.offer((message, None)) >>
                                                      currentBehaviorM

                                                  override def reply[R](response: R) =
                                                    responseChannel.traverse_(_.complete(Right(response)).void) >>
                                                    currentBehaviorM

                                                  override def setState(newState: S) =
                                                    stateRef.update(_ => newState) >> 
                                                    currentBehaviorM

                                                  override def stash() =
                                                    queueMutex.lock.surround:
                                                      stashed.offer((message, responseChannel)) >>
                                                      currentBehaviorM

                                                  override def unstashAll() =
                                                    queueMutex.lock.surround:
                                                      for
                                                        // Take all messages from stash and inbox ...
                                                        stashedMessages  <- stashed.tryTakeN(None)
                                                        inboxMessages    <- inbox.tryTakeN(None)

                                                        // ... and put them into the inbox while making sure the 
                                                        // stashed messages come first.
                                                        _                <- inbox.tryOfferN(stashedMessages)
                                                        _                <- inbox.tryOfferN(inboxMessages)
                                                      yield this.currentBehavior

                        newBehavior          <- currentBehavior
                                                  .receive(state, message, context)
                                                  .recoverWith: error => 
                                                    responseChannel.traverse_(_.complete(Left(error)).void) >>
                                                    IO.pure(currentBehavior)
                                                  
                        _                    <- behaviorRef.set(newBehavior)
                      yield ()).foreverM.void.start

      // Compose the actor
      actor         = new Actor[M]:
                        override def tell(message: M) = 
                          queueMutex.lock.surround:
                            inbox.offer((message, None))

                        override def ask[M2 <: M, R](message: M2, timeout: FiniteDuration)(using Actor.CanAsk[M2, R]) =
                          for
                            responseChannel  <- Deferred[IO, Either[Throwable, Any]]
                            _                <- queueMutex.lock.surround: 
                                                  inbox.offer((message, Some(responseChannel)))
                            output           <- responseChannel.get.timeout(timeout)
                            response         <- IO.fromEither(output)
                            narrowedResponse <- IO(response.asInstanceOf[R])
                          yield narrowedResponse

                        override def terminate = msgLoopFib.cancel
    yield actor
  end spawn

end StatefulActor
