package peloton.actors

import peloton.actor.ActorSystem
import peloton.actor.Actor.*
import peloton.actor.Behavior

import cats.effect.IO
import cats.implicits.*

/**
  * An Actor that collects words
  */
object CollectorActor:

  case class State(words: List[String] = Nil)

  sealed trait Message
  object Message:
    final case class Add(word: String) extends Message
    final case class Get() extends Message

  object Response:
    final case class AddResponse(wordAdded: String)
    final case class GetResponse(words: List[String])

  given CanAsk[Message.Add, Response.AddResponse] = canAsk
  given CanAsk[Message.Get, Response.GetResponse] = canAsk

  // TODO: this talk about different solutions is basically documentation and as such it should be moved to an example file

  // Solution 1: functional
  def handlerFn(words: List[String]): Behavior[State, Message] =
    (state, message, context) => 
      message match
        case Message.Add(word) => 
          context.reply(Response.AddResponse(word)) >> 
          handlerFn(words :+ word).pure

        case Message.Get() => 
          context.reply(Response.GetResponse(words))

  // Solution 2: with a mutable state
  val handler: Behavior[State, Message] =
    (state, message, context) => message match
      case Message.Add(word) => 
        context.setState(State(state.words :+ word)) >> 
        context.reply(Response.AddResponse(word))

      case Message.Get() => 
        context.reply(Response.GetResponse(state.words))

  def spawn(name: String = "CollectorActor")(using actorSystem: ActorSystem) = 
    actorSystem.spawnActor[State, Message](
      name            = Some(name),
      initialState    = State(),
      initialBehavior = handlerFn(words = Nil)
    )
