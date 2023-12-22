package peloton.actor.kernel

import peloton.actor.Actor
import peloton.actor.Actor.*
import peloton.actor.ActorContext
import peloton.actor.Behavior
import peloton.actor.EventSourcedBehavior
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
    * The message processing of the event sourced actor consists of two phases:
    * 
    * 1. the message handler: 
    *   - accepts the incoming messages of type `M`
    *   - uses the provided actor context to possibly reply to the sender (e.g. in case of an ASK pattern)
    *   - perform other actions that *do not* modify the state of the actor
    *   - returns an [[EventAction]] that determines if the message has to be converted into an event (which 
    *     is then written to the event store)
    * 
    * 2. the event handler:
    *   - accepts incoming event (from the message handler or the event store)
    *   - implements the business logic to modify the actor state
    *   - returns the new state
    * 
    * So, in short, the message handler is mainly responsible to convert messages to events (which are then 
    * written to the event store) while the event handler is responsible to update the actor state.
    * 
    * On start, an event sourced actor will read all existing events (for the given persistence ID) from 
    * the event store and replay them using the event handler. This leaves the actor in the same state
    * as before the last shutdown.
    * 
    * Unlike other actor types, the event sourced actor is not allowed to change its behavior. It will always
    * use a behavior of type [[EventSourcedBehavior]]
    *
    * @param persistenceId 
    *   A unique identifier of type [[PersistenceId]] that references the persisted state of this actor in the [[EventStore]].
    * @param initialState 
    *   The initial state for the actor.
    * @param messageHandler 
    *   The message handler of type [[MessageHandler]]
    * @param eventHandler
    *   The event handler of type [[EventHandler]]
    * @param codec 
    *   A given instance of [[PayloadCodec]] to convert instances of the actor's event type `E` to a byte array and vice versa
    * @param eventStore 
    *   A given instance of [[EventStore]]
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

      // Create the new hebavior. This will be static for the whole lifetime of the actor and cannot be changed.
      behavior      = EventSourcedBehavior(persistenceId, messageHandler, eventHandler)

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
                        
                        context               = new ActorContext[S, M](currentBehavior = behavior):
                                                  override def tellSelf(message: M) =
                                                    queueMutex.lock.surround:
                                                      inbox.offer((message, None)) >>
                                                      currentBehaviorM

                                                  override def respond[R](response: R) =
                                                    responseChannel.traverse_(_.complete(Right(response)).void) >>
                                                    currentBehaviorM

                                                  override def setState(newState: S) =
                                                    stateRef.update(_ => newState) >> 
                                                    currentBehaviorM

                                                  override def stash() =
                                                    queueMutex.lock.surround:
                                                      stashed.offer((message, responseChannel)) >>
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
                                                    responseChannel.traverse_(_.complete(Left(error)).void)
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
