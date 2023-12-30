package peloton.actors

import peloton.actor.ActorSystem
import peloton.actor.ActorRef
import peloton.actor.Actor.*
import peloton.actor.Behavior

/**
  * An Actor that can cascade messages by delegating them to another actor
  */
object CascadingActor:

  type State = Unit

  sealed trait Message
  object Message:
    final case class Hello(hello: String) extends Message

  object Response:
    final case class HelloResponse(stack: List[String])

  given CanAsk[Message.Hello, Response.HelloResponse] = canAsk

  private def behavior(name: String, delegate: Option[ActorRef[Message]]): Behavior[State, Message] = 
    (_, message, context) => message match
      case Message.Hello(hello) => 
        delegate match
          case Some(delegate) => 
            for
              delegateResponse <- delegate ? Message.Hello(hello)
              _                <- context.reply(Response.HelloResponse(name :: delegateResponse.stack))
            yield context.currentBehavior

          case None => 
            context.reply(Response.HelloResponse(name :: hello :: Nil))

  def spawn(name: String, delegate: Option[ActorRef[Message]])(using actorSystem: ActorSystem) = 
    actorSystem.spawnActor[Unit, Message](
      name            = Some(name),
      initialState    = (),
      initialBehavior = behavior(name, delegate)
    )
