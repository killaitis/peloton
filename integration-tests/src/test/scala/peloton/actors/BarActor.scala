package peloton.actors

import peloton.*
import peloton.actor.Actor.*

import cats.effect.IO

import peloton.actor.ActorRef
import peloton.actor.ActorSystem
import peloton.persistence.DurableStateStore
import peloton.persistence.JsonPayloadCodec
import peloton.persistence.PayloadCodec
import peloton.persistence.PersistenceId

object BarActor:

  case class State(s: String = "")
  private given PayloadCodec[State] = JsonPayloadCodec.create
    
  sealed trait Message
  case class Set(s: String) extends Message
  case class Get() extends Message
  
  case class SetResponse()
  case class GetResponse(s: String)

  given CanAsk[Set, SetResponse] = canAsk
  given CanAsk[Get, GetResponse] = canAsk

  def spawn(name: String, persistenceId: PersistenceId)(using DurableStateStore)(using actorSystem: ActorSystem): IO[ActorRef[Message]] =
    actorSystem.spawn(
      name            = name,
      persistenceId   = persistenceId, 
      initialState    = State(), 
      initialBehavior = 
        (state, message, context) => message match
          case Set(s) => 
            context.setState(State(s = s)) >> 
            context.reply(SetResponse())

          case Get() => 
            context.reply(GetResponse(state.s))
    )  

end BarActor
