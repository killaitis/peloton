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

object FooActor:

  case class State(x: Int = 0, y: Int = 0)

  sealed trait Message
  object Message:
    case class Set(x: Int, y: Int) extends Message
    case class Get() extends Message
  
  object Response:
    case class SetResponse()
    case class GetResponse(x: Int, y: Int)

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
          case Message.Set(x, y) => 
            context.setState(State(x = x, y = y)) >>
            context.reply(Response.SetResponse())

          case Message.Get() => 
            context.reply(Response.GetResponse(state.x, state.y))
    )
    
end FooActor