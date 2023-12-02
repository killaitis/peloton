package peloton.actors

import peloton.actor.ActorSystem
import peloton.actor.ActorRef
import peloton.actor.Actor.*

/**
  * An Actor that can cascade messages
  */
object CascadingActor:

  sealed trait Message
  
  final case class Hello(hello: String) extends Message
  final case class HelloResponse(stack: List[String])
  given CanAsk[Hello, HelloResponse] = canAsk

  def spawn(name: String, delegate: Option[ActorRef[Message]])(using actorSystem: ActorSystem) = 
    actorSystem.spawn[Unit, Message](
      name = name,
      initialState = (),
      initialBehavior = (_, command, context) => command match
        case Hello(hello) => 
          delegate match
            case Some(delegate) => 
              for
                delegateResponse <- delegate ? Hello(hello)
                _                <- context.respond(HelloResponse(name :: delegateResponse.stack))
              yield context.currentBehavior

            case None => 
              context.respond(HelloResponse(name :: hello :: Nil))
    )
