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

object FooActor:

  case class State(x: Int = 0, y: Int = 0)
  private given PayloadCodec[State] = JsonPayloadCodec.create

  sealed trait Command
  case class Set(x: Int, y: Int) extends Command
  case class Get() extends Command
  
  case class SetResponse()
  case class GetResponse(x: Int, y: Int)

  given CanAsk[Set, SetResponse] = canAsk
  given CanAsk[Get, GetResponse] = canAsk
    
  def spawn(name: String, persistenceId: PersistenceId)(using DurableStateStore)(using actorSystem: ActorSystem): IO[ActorRef[Command]] =
    actorSystem.spawn(
      name            = name,
      persistenceId   = persistenceId, 
      initialState    = State(), 
      initialBehavior = 
        (state, command, context) => 
          command match
            case Set(x, y) => 
              context.setState(State(x = x, y = y)) >>
              context.respond(SetResponse())

            case Get() => 
              context.respond(GetResponse(state.x, state.y))
    )
    
end FooActor