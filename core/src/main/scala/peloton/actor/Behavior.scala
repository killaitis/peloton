package peloton.actor

import cats.effect.IO

/**
  * The actor's behavior.
  * 
  * Behavior is basically a function that takes the current state of the Actor and an incoming message
  * as arguments and returns a new Behavor which is then used by the actor to process the next incoming
  * message.
  * 
  * @tparam S
  *   The type of the actor's internal state
  * @tparam M
  *   The actor's message base type
  */
trait Behavior[S, M]:
  /**
    * The actor's message hander. 
    *
    * @param state 
    *   The current state of the actor
    * @param message 
    *   The incoming message
    * @param context 
    *   The [[ActorContext]]. Can be used to modify the actor's state or stash/unstash messages.
    * @return 
    *   A possibly new [[Behavior]], based on the actor state and the message. If no behavioral change 
    *   is intended, the current behavior can be accessed via [[ActorContext.currentBehavior]].
    */
  def receive(state: S, message: M, context: ActorContext[S, M]): IO[Behavior[S, M]]
