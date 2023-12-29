package peloton.actors

import peloton.actor.Actor.CanAsk
import peloton.actor.Actor.canAsk
import peloton.actor.ActorSystem

import cats.effect.IO

object GreetingActor:

  sealed trait Message

  object Message:
    case class Greet(greeting: String) extends Message
    case object HowAreYou extends Message

  object Response:
    final case class HowAreYouResponse(msg: String)
    
  given CanAsk[Message.HowAreYou.type, Response.HowAreYouResponse] = canAsk
    
  def spawn(name: String)(using actorSystem: ActorSystem) = 
    actorSystem.spawnActor[Boolean, Message](
      name            = Some(name),
      initialState    = false,
      initialBehavior = (alreadyGreeted, message, context) => message match
                          case Message.Greet(greeting) => 
                            IO.println(greeting) >> context.setState(true)
                          case Message.HowAreYou => 
                            if alreadyGreeted 
                            then context.reply(Response.HowAreYouResponse("I'm fine"))
                            else context.reply(Response.HowAreYouResponse("Feeling lonely"))
    )
end GreetingActor
