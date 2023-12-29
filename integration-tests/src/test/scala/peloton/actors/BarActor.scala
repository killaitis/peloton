package peloton.actors

import peloton.*
import peloton.actor.Actor.*

import cats.effect.IO

import peloton.actor.ActorRef
import peloton.actor.ActorSystem
import peloton.persistence.DurableStateStore
import peloton.persistence.KryoPayloadCodec
import peloton.persistence.PayloadCodec
import peloton.persistence.PersistenceId

object BarActor:

  case class State(s: String = "")
    
  sealed trait Message
  object Message:
    case class Set(s: String) extends Message
    case class Get() extends Message

  object Response:
    case class SetResponse()
    case class GetResponse(s: String)

  given CanAsk[Message.Set, Response.SetResponse] = canAsk
  given CanAsk[Message.Get, Response.GetResponse] = canAsk

  private given PayloadCodec[State] = KryoPayloadCodec.create

  def spawn(name: String, persistenceId: PersistenceId)(using DurableStateStore)(using actorSystem: ActorSystem): IO[ActorRef[Message]] =
    actorSystem.spawnDurableStateActor(
      name            = Some(name),
      persistenceId   = persistenceId, 
      initialState    = State(), 
      initialBehavior = 
        (state, message, context) => message match
          case Message.Set(s) => 
            context.setState(State(s = s)) >> 
            context.reply(Response.SetResponse())

          case Message.Get() => 
            context.reply(Response.GetResponse(state.s))
    )  

end BarActor
