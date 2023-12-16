package peloton.cron.actors

import peloton.actor.Actor.CanAsk
import peloton.actor.Actor.canAsk
import peloton.actor.ActorSystem

object CounterActor:
  sealed trait Message

  case object Inc extends Message

  case object Get extends Message
  given CanAsk[Get.type, Int] = canAsk

  def spawn(using actorSystem: ActorSystem) = 
    actorSystem.spawn[Int, Message](
      name = "Timer Actor",
      initialState = 0,
      initialBehavior = (counter, command, context) => command match
        case Inc => context.setState(counter + 1)
        case Get => context.respond(counter)
    )

end CounterActor 
