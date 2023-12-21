package peloton.actor.kernel

import peloton.persistence.PersistenceId
import peloton.persistence.PayloadCodec
import peloton.persistence.Event
import peloton.persistence.EventStore
import peloton.actor.Behavior
import peloton.actor.ActorContext
import peloton.actor.EventAction
import peloton.actor.MessageHandler
import peloton.actor.EventHandler

import cats.effect.IO
import cats.effect.Clock

private [peloton] class EventSourcedBehavior[S, M, E](
          persistenceId: PersistenceId,
          messageHandler: MessageHandler[S, M, E],
          eventHandler: EventHandler[S, E]
        )(using 
          codec: PayloadCodec[E],
          eventStore: EventStore,
          clock: Clock[IO]
          ) extends Behavior[S, M]:
            
  override def receive(state: S, message: M, context: ActorContext[S, M]): IO[Behavior[S, M]] = 
    for
      action   <- messageHandler(state, message, context)
      _        <- action match 
                    case EventAction.Ignore => 
                      IO.unit

                    case EventAction.Snapshot => 
                      IO.unit

                    case EventAction.Store(event) => 
                      for 
                        now      <- clock.realTimeInstant
                        storedEv  = Event(payload = event, timestamp = now.toEpochMilli())
                        _        <- eventStore.writeEvent(persistenceId = persistenceId, 
                                                          event         = storedEv
                                                          )
                        newState  = eventHandler(state, event)
                        _        <- context.setState(newState)
                      yield ()
      newBehavior = this
    yield newBehavior

end EventSourcedBehavior