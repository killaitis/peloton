package peloton.actors

import peloton.actor.Actor.CanAsk
import peloton.actor.Actor.canAsk
import peloton.actor.ActorSystem

import cats.effect.IO

object HelloActor:

  sealed trait Message

  object Message:
    case class Hello(greeting: String) extends Message
    case object HowAreYou extends Message

    final case class HowAreYouResponse(msg: String)
    given CanAsk[HowAreYou.type, HowAreYouResponse] = canAsk

  def spawn(name: String)(using actorSystem: ActorSystem) = 
    actorSystem.spawn[Boolean, Message](
      name            = name,
      initialState    = false,
      initialBehavior = (alreadyGreeted, message, context) => message match
                          case Message.Hello(greeting) => 
                            IO.println(greeting) >> context.setState(true)
                          case Message.HowAreYou => 
                            if alreadyGreeted 
                            then context.respond(Message.HowAreYouResponse("I'm fine"))
                            else context.respond(Message.HowAreYouResponse("Feeling lonely"))
    )
end HelloActor
