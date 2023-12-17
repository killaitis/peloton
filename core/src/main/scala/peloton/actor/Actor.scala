package peloton.actor

import cats.effect.*
import scala.concurrent.duration.*

/**
 * Actors are the basic building blocks of concurrent computation. In response to a message it receives, 
 * an actor can make local decisions, create more actors, send more messages, and determine how to 
 * respond to the next message received. Actors may modify their own private state, but can only 
 * affect each other indirectly through messaging.
 * 
 * Messages sent to an actor are put into a private message inbox. The actor will take each of these messages 
 * and process them sequentially by applying them to the actor's message handler function. The result of the 
 * message handler function may be given back to the sender of the message (ASK pattern, see below). 
 * 
 * There are two different patterns to communicate with an actor:
 * 
 * ==The TELL pattern==
 * 
 * When using the TELL pattern, we just send a single message to the actor (which is very fast) and forget about it.
 * No response is expected to be returned by the actor. This pattern is used for asynchronous communication, 
 * as it is not guaranteed when the message will be processed by the actor. The message is put at the back of the 
 * actor's message queue and processed when all other messages have been processed. 
 * 
 * ===The ASK pattern==
 * 
 * The ASK pattern is used when a response is required from the actor (synchronous communication). A single message
 * is sent to the actor and the current process (the caller) is suspended until the actor has processed the message 
 * and returned a response.
 * 
 * Interacting with an actor is effectful. This is why all actor functions ([[tell]], [[ask]], [[terminate]]) 
 * are wrapped into an effect type.
 * 
 * @tparam M The actor's base message type (message handler input)
 */
abstract class Actor[-M]:

  import Actor.CanAsk

  /**
    * Send a message to the actor using the TELL pattern.
    * 
    * @param message 
    *   A message of the actor's message type `M`
    * @return
    *   An effect that, on evaluation, returns `Unit`
    */ 
  def tell(message: M): IO[Unit]
  
  /**
    * Send a message to the actor using the ASK pattern.
    * 
    * @tparam M2
    *   A covariant subclass of the actor's message type `M`. 
    * @tparam R
    *   The expected response type
    * @param message 
    *   A message of type `M2`
    * @param timeout
    *   In the event that the specified time has passed without the actor returning a response, 
    *   a `TimeoutException` is raised.
    * @param CanAsk
    *   A given instance of [[Actor.CanAsk]] for the type parameters `M2` and `R`. The existence of this 
    *   instance ensures that the actor supports this ask and, if asked with an instance of type `M2`, 
    *   it will answer with an instance of type `R` and not with some different type `R2` or doesn't answer at all
    *   (like in the TELL pattern). In other words, if an actor provides an instance of `CanAsk[M2, R]`,
    *   it guarantees to the client that the ASK pattern for `M2` and `R` is supported by the Actor's protocol. 
    *   As the existence of the CanAsk instance is checked by the compiler, this pattern serves as a 
    *   compile-time guard to avoid invalid message/response usages of the ASK pattern.
    * @return
    *   An effect that, on evaluation, returns a value of the expected message response type `R`
    */ 
  def ask[M2 <: M, R](message: M2, timeout: FiniteDuration = Actor.DefaultTimeout)(using CanAsk[M2, R]): IO[R]
  
  /**
    * Terminate the actor. 
    * 
    * This function is synchronous and effectful. It is guaranteed that the actor is terminated 
    * after evaluating the effect.
    *
    * @return 
    *   an effect that, on evaluation, will terminate the given actor and returns `Unit`
    */ 
  def terminate: IO[Unit]

  /**
    * Send a message to the actor using the TELL pattern.
    * 
    * An alias for [[tell]].
    * 
    * @param message 
    *   A message of the actor's message type `M`
    * @return
    *   An effect that, on evaluation, returns `Unit`
    */ 
  inline def ! (message: M): IO[Unit] = tell(message)

  /**
    * Send a message to the actor using the ASK pattern and convert the result to a specific narrower response type.
    * 
    * An alias for [[ask]].
    * 
    * @tparam M2
    *   A covariant subclass of the actor's message type. 
    * @tparam R
    *   The expected response type.
    * @param message 
    *   A message of type `M2`
    * @param timeout
    *   In the event that the specified time has passed without the actor returning a response, 
    *   a `TimeoutException` is raised.
    * @param CanAsk
    *   A given instance of [[Actor.CanAsk]] for type parameters `M2` and `R`. This ensures that 
    *   the actor supports the function from `M2` and `R`, i.e., if asked with `M2` it 
    *   will answer with `R` and not with some different `R2`
    * @return
    *   An effect that, on evaluation, returns a value of the response type `R`
    */ 
  inline def ? [M2 <: M, R](message: M2, timeout: FiniteDuration = Actor.DefaultTimeout)(using CanAsk[M2, R]): IO[R] = 
    ask[M2, R](message, timeout)

end Actor

object Actor:

  val DefaultTimeout: FiniteDuration = 1.hour

  /**
    * This class is used to establish a connection between a message class and the related response class 
    * using the ASK pattern. [[Actor.ask]] requires a given instance of `CanAsk`. This ensures at *COMPILE TIME*
    * that if [[Actor.ask]] is called with a message of `M`, an instance of `R` (or a failed/cancelled `IO`) is 
    * guaranteed to be returned.
    * 
    * Example:
    *
    * {{{
    * sealed trait Message
    * 
    * case class MyMessage() extends Message
    * case class MyOtherMessage() extends Message
    * case class MyResponse()
    * 
    * given CanAsk[MyMessage, MyResponse] = canAsk
    * 
    * ...
    * 
    * for
    *   myActor    <- ...                         // spawn an Actor[Message]
    *   myResponse <- myActor ? MyMessage()       // this will compile, as there is a given instance of
    *                                             // CanAsk[MyMessage, MyResponse]. The Compiler will also know 
    *                                             // that myResponse os of type MyResponse, not generic type Response.
    *   myResponse <- myActor ? MyOtherMessage()  // this will NOT compile, as there is no given instance of 
    *                                             // CanAsk[MyOtherMessage, ?].
    *   _          <- myActor ! MyOtherMessage()  // this will compile, as TELL does not require any givens
    * yield ()
    * }}}
    */
  final class CanAsk[M, R]

  inline def canAsk[M, R] = CanAsk[M, R]()

  /**
    * Terminate a given [[Actor]]
    * 
    * This function is synchronous and effectful. It is guaranteed that the actor is terminated 
    * after evaluating the effect.
    *
    * @param actor 
    *   The actor to terminate
    * @return 
    *   an effect that, on evaluation, will terminate the given actor and evaluate to `Unit`
    */
  inline def terminate(actor: Actor[?]): IO[Unit] = actor.terminate

end Actor