package peloton.actor

import cats.effect.*
import cats.implicits.*

/**
  * The actor's context provides some helper functions for its message handler (`Behavior`)
  * 
  * An instance of the actor context is passed to the actor's message handler, so the handler can use its 
  * helper functions.
  *
  * @tparam S
  *   The type of the actor's internal state
  * @tparam M
  *   The actor's message base type
  */
trait ActorContext[S, M](val currentBehavior: Behavior[S, M]):
  /**
    * Send a message of the actor's message type `M` to the actor which is connected to this context.
    * 
    * Can be used to seld a message from within the actor's message handler ([[Behavior]]) to the same actor.
    * The message will be enqueued at the end of the actor's message queue.
    * 
    * @note: Due to the nature of message processing, there can be no such thing as `askSelf`, for this would requre to 
    * interrupt the processing of the current message, process all other messages already in the message queue, then 
    * process the asked message and then, finally, resume the interrupted processing of the initial message. This would
    * heavily violate the Actor law that all messages are guaranteed to be processed in order of arrival. 
    *
    * @param message 
    *   A message of the actor's message type `M`
    * @return 
    *   The current behavior of the actor.
    */
  def tellSelf(message: M): IO[Behavior[S, M]]

  /**
    * An alias for [[tellSelf]]
    *
    * @param message 
    *   A message of the actor's message type `M`
    * @return 
    *   The current behavior of the actor.
    */
  inline def ! (message: M) = tellSelf(message)

  /**
    * Send a response back to the sender of this message. 
    *
    * @param response 
    *   The response message
    * @return
    *   The current behavior of the actor.
    */
  def respond[R](response: R): IO[Behavior[S, M]]
  
  /**
    * Replace the current state of the actor with a new state. 
    * 
    * Under the hood, the current state might be either stored in memory (like in the [[peloton.actor.kernel.StatefulActor]]) 
    * or on a persistent storage (like in [[peloton.actor.kernel.PersistentActor]]).
    *
    * @param newState 
    *   The new state
    * @return
    *   The current behavior of the actor.
    */
  def setState(newState: S): IO[Behavior[S, M]]

  /**
    * Takes the message which is currently processed by the message handler and puts it on the actor's 
    * message stash. This allows it to unstash and process the message later, depending on the current
    * state of the actor. 
    *
    * @return
    *   The current behavior of the actor.
    */
  def stash(): IO[Behavior[S, M]]

  /**
    * Moves all messages from the actor's message stash back to the end of the message inbox.
    *
    * @return
    *   The current behavior of the actor.
    */
  def unstashAll(): IO[Behavior[S, M]]

  /**
    * Waits asynchronously for the completion/cancellation/abortion of a given effect, transforms the outcome 
    * into a list of messages that is compatible with the actor's message type `M` and sends these messages to the actor.
    * 
    * @tparam A 
    *   The value type of the effect
    * @param effect 
    *   An effect of type `A`
    * @param mapOutcome
    *   A function that maps the outcome of the evaluation of the given effect to a list of messages of the actor's message type `M`
    *   The [[cats.effect.Outcome]] is either 
    *   - Canceled
    *   - Errored
    *   - Succeeded.
    * @return
    *   The fiber of the given effect that has been started in the background, wrapped in an IO effect.
    */
  def pipeToSelf[A](effect: IO[A])(mapOutcome: OutcomeIO[A] => IO[List[M]]): IO[FiberIO[Unit]] = 
    (
      effect
        .guaranteeCase: outcome => 
          for
            msg <- mapOutcome(outcome)
            _   <- msg.traverse_(tellSelf)
          yield ()
        .void
        .handleErrorWith(_ => IO.unit)
    ).start

end ActorContext
