package peloton.actor.internal

import peloton.actor.Behavior
import peloton.actor.ActorContext
import peloton.persistence.EventAction
import peloton.persistence.PersistenceId
import peloton.persistence.PayloadCodec
import peloton.persistence.Event
import peloton.persistence.EventStore
import peloton.persistence.Retention
import peloton.persistence.Snapshot
import peloton.persistence.MessageHandler
import peloton.persistence.EventHandler
import peloton.persistence.SnapshotPredicate

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Clock


private [internal] class EventSourcedBehavior[S, M, E](
          persistenceId: PersistenceId,
          messageHandler: MessageHandler[S, M, E],
          eventHandler: EventHandler[S, E],
          snapshotPredicate: SnapshotPredicate[S, E],
          retention: Retention,
          eventCounterRef: Ref[IO, Int]
        )(using 
          eventStore: EventStore,
          eventCodec: PayloadCodec[E],
          snapshotCodec: PayloadCodec[S],
          clock: Clock[IO]
         ) extends Behavior[S, M]:
            
  override def receive(state: S, message: M, context: ActorContext[S, M]): IO[Behavior[S, M]] = 
    for
      action   <- messageHandler(state, message, context)
      _        <- action match 
                    case EventAction.Ignore => 
                      IO.unit

                    case EventAction.Persist(event) => 
                      for 
                        // Write the event to the event store
                        now                  <- clock.realTimeInstant
                        storedEvent           = Event(payload   = event, 
                                                      timestamp = now.toEpochMilli
                                                     )
                        _                    <- eventStore.writeEvent(persistenceId = persistenceId, 
                                                                      event         = storedEvent
                                                                      )
                        newState              = eventHandler(state, event)
                        _                    <- context.setState(newState)

                        // Do snapshotting
                        currentEventCounter  <- eventCounterRef.get
                        doSnapshot            = snapshotPredicate(newState, event, currentEventCounter + 1)
                        newEventCounter      <- 
                                                if doSnapshot then
                                                  for 
                                                    now      <- clock.realTimeInstant
                                                    snapshot  = Snapshot(payload   = newState, 
                                                                         timestamp = now.toEpochMilli
                                                                        )
                                                    _        <- eventStore.writeSnapshot(persistenceId = persistenceId, 
                                                                                         snapshot      = snapshot,
                                                                                         retention     = retention
                                                                                        )
                                                  yield 0
                                                else 
                                                  IO.pure(currentEventCounter + 1)
                        _                    <- eventCounterRef.set(newEventCounter)
                      yield ()
      newBehavior = this
    yield newBehavior

end EventSourcedBehavior
