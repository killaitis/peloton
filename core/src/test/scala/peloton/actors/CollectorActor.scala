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

  final case class Add(word: String) extends Message
  final case class AddResponse(wordAdded: String)
  given CanAsk[Add, AddResponse] = canAsk

  final case class Get() extends Message
  final case class GetResponse(words: List[String])
  given CanAsk[Get, GetResponse] = canAsk

  // TODO: this talk about different solutions is basically documentation and as such it should be moved to an example file

  // Solution 1 (preferred): functional
  def handlerFn(words: List[String]): Behavior[State, Message] =
    (state, message, context) => 
      message match
        case Add(word) => 
          context.reply(AddResponse(word)) >> 
          handlerFn(words :+ word).pure

        case Get() => 
          context.reply(GetResponse(words))

  // Solution 2: with a modifiable state
  val handler: Behavior[State, Message] =
    (state, message, context) => message match
      case Add(word) => 
        context.setState(State(state.words :+ word)) >> 
        context.reply(AddResponse(word))

      case Get() => 
        context.reply(GetResponse(state.words))

  def spawn(name: String = "CollectorActor")(using actorSystem: ActorSystem) = 
    actorSystem.spawn[State, Message](
      name = name,
      initialState = State(),
      initialBehavior = handlerFn(words = Nil)
    )
