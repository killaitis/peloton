package peloton.actor.kernel

import cats.effect.*
import cats.effect.std.Queue
import cats.effect.std.Mutex
import cats.implicits.*

import scala.concurrent.duration.FiniteDuration

import peloton.actor.Actor
import peloton.actor.Actor.*
import peloton.actor.ActorContext
import peloton.actor.Behavior
import peloton.persistence.DurableStateStore
import peloton.persistence.PayloadCodec
import peloton.persistence.PersistenceId


private [peloton] object PersistentActor:

  import DurableStateStore.*

  /**
    * Type of the elements of the internal message queues. Each element consists of a pair of 
    * - the incoming raw message sent to the actor of type `M`
    *  - a 'something' to transport a possible actor response back to the caller. We use 
    *    a [[Deferred]] here where the consumer (the client) will listen and wait while the 
    *    producer (the actor) will at some point in time send a response. The response is
    *    either the actor's response or an error (`Throwable`). The `Deferred` 
    *    is finally wrapped into an `Option`. This allows it to skip the whole creation of a
    *    `Deferred` when no response is needed (`tell()`) and only allocate it if needed (`ask()`)
    */
  private type ActorMessage[M] = (M, Option[Deferred[IO, Either[Throwable, Any]]])

  /**
    * Spawns a new [[Actor]] with persistent state.
    *
    * The `PersistentActor` is connected to a given [[DurableStateStore]] instance. A given `PersistenceId` 
    * connects this actor instance to a distinct record in the state store, e.g., database row with index key.
    * 
    * When the actor spawns, the last known previous actor state is read from the store and used as the 
    * initial state of the actor. If no previous state was found, a given default state is used.
    * 
    * While processing incoming messages, the actor's Behavior can use [[Context.setState]] to update the 
    * state's representation in the `DurableStateStore`.
    * 
    * @tparam S 
    *   The type of the actor's internal state
    * @tparam M
    *   The actor's message type
    * @param persistenceId 
    *   A unique identifier of type [[PersistenceId]] that references the persisted state of this actor in the [[DurableStateStore]].
    * @param initialState 
    *   A default/initial state that is used if the actor has never stored its state with the given `persistenceId`.
    * @param initialBehavior 
    *   The initial behavior, i.e., the message handler function. The function takes the current state of the actor, 
    *   the input (message) and the actor's [[Context]] as parameters and returns a new [[Behavior]], depending on 
    *   the state and the message. The behavior is evaluated effectful.
    * @param codec 
    *   A given [[PayloadCodec]] to convert instances of the actor's durable state type `S` to a byte array and vice versa
    * @param store 
    *   A given instance of [[DurableStateStore]]
    * @return 
    *   An effect that creates a new [[Actor]]
    */
  def spawn[S, M](persistenceId: PersistenceId,
                  initialState: S,
                  initialBehavior: Behavior[S, M]
                 )(using 
                  codec: PayloadCodec[S],
                  store: DurableStateStore
                 ): IO[Actor[M]] =
    for
      // Wrap the current behavior into a Ref
      behaviorRef  <- Ref.of[IO, Behavior[S, M]](initialBehavior)

      // Create the state of the actor - either by reading it from the durable state store or by using the default value
      clock         = summon[Clock[IO]]
      now          <- clock.realTimeInstant
      state        <- store
                        .read(persistenceId)
                        .map(_.getOrElse(DurableState(initialState, revision = 0, timestamp = now.toEpochMilli())))

      // Wrap the state into a Ref, so both the client (producer) and the message handler loop (consumer) can access it thread-safely.
      stateRef     <- Ref.of[IO, DurableState[S]](state)

      // Create the message queues for both the inbox and the stash. 
      inbox        <- Queue.unbounded[IO, ActorMessage[M]]
      stashed      <- Queue.unbounded[IO, ActorMessage[M]]

      // Use a Mutex to guard all access to both inbox and stash, i.e., make access atomic. This is neccessary to ensure that 
      // messages are always enquened in orrect order while moving messages from stash to inbox or vice versa.
      queueMutex   <- Mutex[IO]
      
      // Create the message processing loop and spawn it in the background (fiber)
      msgLoopFib   <- (for
                        state                <- stateRef.get
                        (message, deferred)  <- inbox.take

                        currentBehavior      <- behaviorRef.get
                        
                        context               = new ActorContext[S, M](currentBehavior = currentBehavior):
                                                  override def tellSelf(message: M) =
                                                    queueMutex.lock.surround:
                                                      inbox.offer((message, None)) >> 
                                                      this.currentBehavior.pure

                                                  override def respond[R](response: R) =
                                                    deferred.traverse_(_.complete(Right(response)).void) >>
                                                    this.currentBehavior.pure

                                                  override def setState(newState: S) = 
                                                    for
                                                      now              <- clock.realTimeInstant
                                                      newDurableState   = DurableState(payload = newState, 
                                                                                       revision = state.revision + 1, 
                                                                                       timestamp = now.toEpochMilli()
                                                                                      )
                                                      _                <- store.write(persistenceId, newDurableState)
                                                      _                <- stateRef.update(_ => newDurableState)
                                                    yield this.currentBehavior

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

                        newBehavior          <- currentBehavior
                                                  .receive(state.payload, message, context)
                                                  .recoverWith: error => 
                                                    deferred.traverse_(_.complete(Left(error)).void) >>
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
                            deferred         <- Deferred[IO, Either[Throwable, Any]]
                            _                <- queueMutex.lock.surround:
                                                  inbox.offer((message, Some(deferred)))
                            output           <- deferred.get.timeout(timeout)
                            response         <- IO.fromEither(output)
                            narrowedResponse <- IO(response.asInstanceOf[R])
                          yield narrowedResponse

                        override def terminate = msgLoopFib.cancel
      
    yield actor
  end spawn

end PersistentActor
