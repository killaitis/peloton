package peloton.cron.actors

import peloton.actor.Actor.CanAsk
import peloton.actor.Actor.canAsk
import peloton.actor.ActorSystem
import peloton.actor.Behavior

/**
  * An Actor that holds a counter that can be 
  * - incremented by 1 by sending an Inc message
  * - read by asking with a Get message
  */
object CounterActor:
  sealed trait Message

  case object Inc extends Message

  case object Get extends Message
  given CanAsk[Get.type, Int] = canAsk

  private val behavior: Behavior[Int, Message] = 
    (counter, message, context) => message match
      case Inc => context.setState(counter + 1)
      case Get => context.reply(counter)

  def spawn(using actorSystem: ActorSystem) = 
    actorSystem.spawn[Int, Message](
      name            = "Counter Actor",
      initialState    = 0,
      initialBehavior = behavior
    )

end CounterActor 
