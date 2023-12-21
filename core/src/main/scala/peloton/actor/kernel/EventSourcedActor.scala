package peloton.actor.kernel

import peloton.actor.Actor
import peloton.actor.Actor.*
import peloton.actor.ActorContext
import peloton.actor.Behavior
import peloton.actor.MessageHandler
import peloton.actor.EventHandler
import peloton.persistence.PersistenceId
import peloton.persistence.EventStore
import peloton.persistence.PayloadCodec

import cats.effect.*
import cats.effect.std.Queue
import cats.effect.std.Mutex
import cats.implicits.*

import scala.concurrent.duration.FiniteDuration

private [peloton] object EventSourcedActor:

  /**
    * Spawn a new [[Actor]] with event sourced behavior. 
    * 
    * The actor maintains an internal state which is passed to the message handler an can be updated using
    * the context's [[Context.setState]] method. Each message sent to the actor is stored in the event store
    * before being passed to the message handler. On start, all stored messageged for the given persistence ID
    * are fetched from the event store and passed to the actor's message handler. Thus, the actor's state will be 
    * re-created like it was after it was shut down before.
    *
    * @param persistenceId 
    *   A unique identifier of type [[PersistenceId]] that references the persisted state of this actor in the [[EventStore]].
    * @param initialState 
    *   The initial state for the actor.
    * @param initialBehavior 
    *   The initial behavior, i.e., the message handler function. The function takes the current state of the actor, 
    *   the input (message) and the actor's [[Context]] as parameters and returns a new [[Behavior]], depending on 
    *   the state and the message. The behavior is evaluated effectful.
    * @param codec 
    *   A given [[PayloadCodec]] to convert instances of the actor's message type `M` to a byte array and vice versa
    * @param eventStore 
    *   A given instance of [[EvetStore]]
    * @return 
    *   An effect that creates a new [[Actor]]
    */
  def spawn[S, M, E](persistenceId: PersistenceId,
                     initialState: S,
                     messageHandler: MessageHandler[S, M, E],
                     eventHandler: EventHandler[S, E]
                    )(using 
                     codec: PayloadCodec[E],
                     eventStore: EventStore
                    ): IO[Actor[M]] =
    for
      // Wrap the state into a Ref, so both the client (producer) and the message handler loop (consumer) can access it thread-safely.
      stateRef     <- Ref.of[IO, S](initialState)
      behavior      = new EventSourcedBehavior(persistenceId, messageHandler, eventHandler)

      // Create the message queues for both the inbox and the stash. 
      inbox        <- Queue.unbounded[IO, ActorMessage[M]]
      stashed      <- Queue.unbounded[IO, ActorMessage[M]]

      // Use a Mutex to guard all access to both inbox and stash, i.e., make access atomic. This is neccessary to ensure that 
      // messages are always enquened in orrect order while moving messages from stash to inbox or vice versa.
      queueMutex   <- Mutex[IO]

      // Create the message processing loop and spawn it in the background (fiber)
      msgLoopFib   <- (for
                        (message, deferred)  <- inbox.take
                        state                <- stateRef.get
                        
                        context               = new ActorContext[S, M](currentBehavior = behavior):
                                                  override def tellSelf(message: M) =
                                                    queueMutex.lock.surround:
                                                      inbox.offer((message, None)) >>
                                                      this.currentBehavior.pure

                                                  override def respond[R](response: R) =
                                                    deferred.traverse_(_.complete(Right(response)).void) >>
                                                    this.currentBehavior.pure

                                                  override def setState(newState: S) =
                                                    stateRef.update(_ => newState) >> 
                                                    this.currentBehavior.pure

                                                  override def stash() =
                                                    queueMutex.lock.surround:
                                                      stashed.offer((message, deferred)) >>
                                                      this.currentBehavior.pure

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

                        _                    <- behavior
                                                  .receive(state, message, context)
                                                  .recoverWith: error => 
                                                    deferred.traverse_(_.complete(Left(error)).void)
                      yield ()).foreverM.void.start

      // Compose the actor
      actor         = new Actor[M]:
                        override def tell(message: M) =
                          queueMutex.lock.surround:
                            inbox.offer((message, None))

                        override def ask[M2 <: M, R](message: M2, timeout: FiniteDuration)(using Actor.CanAsk[M2, R]) =
                          for
                            deferred         <- Deferred[IO, Either[Throwable, Any]]
                            _                <- queueMutex.lock.surround:
                                                  inbox.offer((message, Some(deferred)))
                            output           <- deferred.get.timeout(timeout)
                            response         <- IO.fromEither(output)
                            narrowedResponse <- IO(response.asInstanceOf[R])
                          yield narrowedResponse

                        override def terminate = msgLoopFib.cancel

      // Read all previous events from the event store and put them into the actor's message queue
      state        <- stateRef.get
      agg          <- queueMutex.lock.surround:
                        eventStore
                          .readEvents(persistenceId)
                          .fold(state)((s, event) => eventHandler(s, event.payload))
                          .compile
                          .toList
      newState      = agg.head
      _            <- stateRef.set(newState)
    yield actor
  end spawn

end EventSourcedActor
