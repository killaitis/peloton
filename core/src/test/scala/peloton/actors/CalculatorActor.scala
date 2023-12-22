package peloton.actors

import cats.effect.IO

import peloton.actor.Actor
import peloton.actor.ActorRef
import peloton.actor.Actor.*
import peloton.actor.ActorSystem
import peloton.actor.EventAction
import peloton.persistence.PersistenceId
import peloton.persistence.EventStore
import peloton.persistence.PayloadCodec
import peloton.persistence.JsonPayloadCodec
import peloton.actor.ActorContext

/**
  * A event sourced actor with simple calculation operations 
  */
object CalculatorActor:

  sealed trait Message

  case class Add(value: Int) extends Message
  case class Sub(value: Int) extends Message
  
  case object GetState extends Message
  case class GetStateResponse(value: Int)
  given CanAsk[GetState.type, GetStateResponse] = canAsk
  
  case class State(value: Int = 0)

  sealed trait Event
  case class AddEvent(value: Int) extends Event
  case class SubEvent(value: Int) extends Event

  given PayloadCodec[Event] = JsonPayloadCodec.create

  private def messageHandler(state: State, message: Message, context: ActorContext[State, Message]): IO[EventAction[Event]] = 
    message match
      case Add(value) =>
        IO.pure(EventAction.Persist(AddEvent(value)))

      case Sub(value) =>
        IO.pure(EventAction.Persist(SubEvent(value)))

      case GetState =>
        context.respond(GetStateResponse(state.value)) >> 
        IO.pure(EventAction.Ignore)


  private def eventHandler(state: State, event: Event): State = 
    event match
      case AddEvent(value) => State(state.value + value)
      case SubEvent(value) => State(state.value - value)


  def spawn(persistenceId: PersistenceId, name: String = "CalculatorActor")(using EventStore)(using actorSystem: ActorSystem): IO[ActorRef[Message]] =
    for
      actor        <- actorSystem.spawn[State, Message, Event](
                        name            = name,
                        persistenceId   = persistenceId, 
                        initialState    = State(),
                        messageHandler  = messageHandler,
                        eventHandler    = eventHandler
                      )
    yield actor
  