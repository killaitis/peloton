import peloton.actor.*
import peloton.actor.Actor.*

import cats.effect.IO
import cats.implicits.*

import WordCollectorActor.*


// This example shows how you can handle an actor's state in both a functional style 
// and with a mutable state. 
// 
// Both approaches have their own advantages and disadvantages. 
// 
// The advantage of having a behavior-generating function is that you have all 
// values needed by the handler in the parameter list. Other behavior functions 
// might require different parameters. Using a state, you'd have to model all 
// these parameters into one state class and update it partially, which, in case 
// of a more complex actor logic, it is more likely to make mistakes.
// 
// The disadvantage is that for a simple actor logic (like this example) the 
// functional style requires slightly more code.
// 
// This example shows two different implementions for an actor that receives words 
// (Strings) via messages and collects them.
// 
// First, we define the protocol that both actors will use. 
object WordCollectorActor:
  sealed trait Message
  object Message:
    final case class AddWord(word: String) extends Message
    case object GetWords extends Message

  given CanAsk[Message.GetWords.type, List[String]] = canAsk


// An implementation of the WordCollectorActor protocol using a functional style 
// (functions that return a new behavior) to hold the state (the word list). 
// It does not use the state that is automatically provided by each actor at all, 
// so the state is set to `Unit`.
object FunctionalWordCollectorActor:

  // The behavior. Note that it is implemented as a function instead of a constant.
  // The current list of collected words is passed as a parameter and when a new 
  // word is added via the Add message, the collect function is applied to the 
  // updated list of words to createte new behavior.
  private def collect(words: List[String]): Behavior[Unit, Message] =
    (_, message, context) => 
      message match
        case Message.AddWord(word) => 
          // !!! Don't touch the actor state, but return a NEW behavior. !!!
          collect(words :+ word).pure

        case Message.GetWords => 
          context.reply(words)

  // Spawn the actor. Each actor has a mutable state which needs an initial value.
  // We will not use it and just set it to Unit. The initial behavior is set to a
  // call of collect() with an empty word list (which is used as our initial state).
  def spawn()(using actorSystem: ActorSystem) = 
    actorSystem.spawnActor[Unit, Message](
      initialState    = (),
      initialBehavior = collect(words = Nil)
    )

end FunctionalWordCollectorActor


// An implementation of the WordCollectorActor protocol using the mutable state 
// provided by the actor to hold the word list.
object MutableStateWordCollectorActor:

  type State = List[String]

  // The behavior. As it will never change, we just use a val.
  // Most methods of the actor context (like setState() or reply()) return the 
  // current behavior, so - like in this example - there is no need to 
  // explicitly return it (e.g. via context.currentBehavior or 
  // context.currentBehaviorM).
  private val behavior: Behavior[State, Message] =
    (words, message, context) => message match
      case Message.AddWord(word) => 
        // !!! Modify the actor state and return the SAME behavior. !!!
        context.setState(words :+ word)

      case Message.GetWords => 
        context.reply(words)

  // Spawn the actor and use its state to store the word list.
  def spawn()(using actorSystem: ActorSystem) = 
    actorSystem.spawnActor[State, Message](
      initialState    = Nil,
      initialBehavior = behavior
    )

end MutableStateWordCollectorActor