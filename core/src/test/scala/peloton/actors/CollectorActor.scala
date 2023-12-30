package peloton.actors

import peloton.actor.ActorSystem
import peloton.actor.Actor.*
import peloton.actor.Behavior

/**
  * An Actor that collects words
  */
object CollectorActor:

  final case class State(words: List[String] = Nil)

  sealed trait Message
  object Message:
    final case class Add(word: String) extends Message
    case object Get extends Message

  object Response:
    final case class AddResponse(wordAdded: String)
    final case class GetResponse(words: List[String])

  given CanAsk[Message.Add,      Response.AddResponse] = canAsk
  given CanAsk[Message.Get.type, Response.GetResponse] = canAsk

  val behavior: Behavior[State, Message] =
    (state, message, context) => message match
      case Message.Add(word) => 
        context.setState(State(state.words :+ word)) >> 
        context.reply(Response.AddResponse(word))

      case Message.Get => 
        context.reply(Response.GetResponse(state.words))

  def spawn(name: String = "CollectorActor")(using actorSystem: ActorSystem) = 
    actorSystem.spawnActor[State, Message](
      name            = Some(name),
      initialState    = State(),
      initialBehavior = behavior
    )
