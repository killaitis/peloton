package peloton.actor

import peloton.persistence.PersistenceId
import peloton.persistence.PayloadCodec
import peloton.persistence.Event
import peloton.persistence.EventStore
import peloton.actor.MessageHandler
import peloton.actor.EventHandler

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Clock
import peloton.persistence.Snapshot

enum EventAction[+E]:
  case Ignore
  case Persist(event: E)

type MessageHandler[S, M, E] = (state: S, message: M, context: ActorContext[S, M]) => IO[EventAction[E]]
type EventHandler[S, E] = (state: S, event: E) => S
type SnapshotPredicate[S, E] = (state: S, event: E, numEvents: Int) => Boolean

object SnapshotPredicate:
  def noSnapshots[S, E]: SnapshotPredicate[S, E] = (_, _, _) => false
  def each[S, E](n: Int): SnapshotPredicate[S, E] = (_, _, count) => count % n == 0

private [peloton] class EventSourcedBehavior[S, M, E](
          persistenceId: PersistenceId,
          messageHandler: MessageHandler[S, M, E],
          eventHandler: EventHandler[S, E],
          snapshotPredicate: SnapshotPredicate[S, E],
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
                                                                                         snapshot      = snapshot
                                                                                        )
                                                  yield 0
                                                else 
                                                  IO.pure(currentEventCounter + 1)
                        _                    <- eventCounterRef.set(newEventCounter)
                      yield ()
      newBehavior = this
    yield newBehavior

end EventSourcedBehavior
