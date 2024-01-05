package peloton.actors

import cats.effect.IO

import peloton.actor.Actor
import peloton.actor.Actor.{CanAsk, canAsk}
import peloton.actor.ActorContext
import peloton.actor.ActorRef
import peloton.actor.ActorSystem
import peloton.persistence.PersistenceId
import peloton.persistence.EventStore
import peloton.persistence.EventAction
import peloton.persistence.PayloadCodec
import peloton.persistence.KryoPayloadCodec
import peloton.persistence.SnapshotPredicate

/**
  * An event sourced actor with simple calculation operations 
  */
object CalculatorActor:

  // --- State
  final case class State(value: Int = 0)

  // --- Protocol
  sealed trait Message
  object Message:
    final case class Add(value: Int) extends Message
    final case class Sub(value: Int) extends Message
    
    case object GetState extends Message
  
  object Response:
    final case class GetStateResponse(value: Int)

  given CanAsk[Message.GetState.type, Response.GetStateResponse] = canAsk

  // --- Events
  sealed trait Event
  object Event:
    final case class Add(value: Int) extends Event
    final case class Sub(value: Int) extends Event

  given PayloadCodec[Event] = KryoPayloadCodec.create
  given PayloadCodec[State] = KryoPayloadCodec.create

  private def messageHandler(state: State, message: Message, context: ActorContext[State, Message]): IO[EventAction[Event]] = 
    message match
      case Message.Add(value) =>
        EventAction.persist(Event.Add(value))

      case Message.Sub(value) =>
        EventAction.persist(Event.Sub(value))

      case Message.GetState =>
        context.reply(Response.GetStateResponse(state.value)) >> 
        EventAction.ignore

  private def eventHandler(state: State, event: Event): State = 
    event match
      case Event.Add(value) => State(state.value + value)
      case Event.Sub(value) => State(state.value - value)

  def spawn(persistenceId: PersistenceId, 
            name: String = "CalculatorActor",
            snapshotPredicate: SnapshotPredicate[State, Event] = SnapshotPredicate.noSnapshots
           )(using EventStore)(using actorSystem: ActorSystem): IO[ActorRef[Message]] =
    actorSystem.spawnEventSourcedActor[State, Message, Event](
      name              = Some(name),
      persistenceId     = persistenceId, 
      initialState      = State(),
      messageHandler    = messageHandler,
      eventHandler      = eventHandler,
      snapshotPredicate = snapshotPredicate
    )
  