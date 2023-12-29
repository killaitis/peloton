import peloton.actor.ActorSystem
import peloton.actor.Actor.*
import peloton.actor.Behavior

import cats.effect.IOApp
import cats.effect.IO

object HelloActor:

  // Peloton actors hold an internal state of a specific type. 
  // Our little HelloActor will not use any state, so we simply use Unit
  type State = Unit 

  // Peloton actors are typed. They will only accept messages of a specific 
  // (contravariant) type. For our HelloActor, we will accept messages of
  // `Message` subtypes.
  sealed trait Message

  // These are the two message types the actor will accept.
  // The Hello actor API/protocol will use both the TELL and the ASK pattern.
  object Message:
    // - Hello messages are processed using the TELL pattern, so no response will be provided.
    final case class Hello(greeting: String) extends Message
    // - HowAreYou messages are processed using the ASK pattern and reply with a HowAreYouResponse.
    case object HowAreYou extends Message


  object Response:
    // The response type for the HowAreYou message. Peloton actors can reply with any type, 
    // including basic types like String or Int. For demonstration purpose, a case class is 
    // used here.
    final case class HowAreYouResponse(msg: String)

  // Unlike for the TELL pattern where no response in involved, Peloton tries its best
  // to ensure that the compiler knows that the actor supports the ASK pattern for a 
  // specific message type and what type will be returned. This is especially important
  // as Peloton supports *any* response types (and you definitely do not want to have 
  // actors replying with `Any` types). 
  // 
  // Peloton solves this with the help of the CanAsk type class. You as the provider of the 
  // actor's protocol have to also provide explicit information about which messages will
  // allow the ASK pattern and what will be the response type. You do this by defining
  // given instances of CanAsk with both the message and response type. 
  given CanAsk[Message.HowAreYou.type, Response.HowAreYouResponse] = canAsk

  // The behavior of the HelloActor. Behavior is basically a function that takes the current 
  // state of the actor and a message from the actor's message inbox as arguments and
  // returns a new behavior (or the current behavior if no change in behavior is required, 
  // like in our HelloWorld actor).
  private val behavior: Behavior[State, Message] = 
    (_, message, context) => message match
      case Message.Hello(greeting) => 
        IO.println(greeting) >> 
        context.currentBehaviorM // return the same behavior
        
      case Message.HowAreYou => 
        // reply implicitly returns the current behavior, so no need for an extra 
        // context.currentBehaviorM here.
        context.reply(Response.HowAreYouResponse("I'm fine")) 

  // The factory method for HelloActors. Note that it expects a given ActorSystem where it will
  // reside. While you *can* explicitly terminate an actor, it will be terminated automatically
  // when the ActorSystem is shut down.
  def spawn()(using actorSystem: ActorSystem) = 
    actorSystem.spawnActor[Unit, Message](
      name            = Some("HelloActor"),
      initialState    = (),
      initialBehavior = behavior
    )
    
end HelloActor

object HelloWorld extends IOApp.Simple:

  def run: IO[Unit] = 
    // An actor system is a (Cats) resource and has to be 'used'. This `use` bracket
    // defines the lifetime of all resources (mainly actors created here) in the actor 
    // system, i.e., all actors will be terminated automatically after the `use` bracket.
    // Note the `?=>` operator which makes the lambda parameter implicit. Most Peloton
    // classes require a given instance of the actor system, so it is usually hidden 
    // from your code (and therefore omitted here with `_`).
    ActorSystem.use: _ ?=> 
      for
                      // Spawn a new actor
        helloActor <- HelloActor.spawn()

                      // Send a `Hello` message to the actor using the TELL pattern.
                      // TELL means that the message is just sent and no response 
                      // is expected, so the operation finished immediately after the
                      // message is put into the actor's message inbox. This does not
                      // mean that the actor has already processed the message at this
                      // point.
        _          <- helloActor ! HelloActor.Message.Hello("Hello, World!")

                      // Send `HowAreYou` message to the actor using the ASK pattern.
                      // ASK means that the message is sent to the actor (like TELL),
                      // but the operation is suspended until the actor replied with
                      // a response. Actors process messages in the same order they 
                      // were put into the message inbox, so when the ASK operation
                      // has finished, it is guaranteed that all messages that were 
                      // previously sent to the actor have been processed as well.
        response   <- helloActor ? HelloActor.Message.HowAreYou
        _          <- IO.println(s"The Hello actor says: $response")
      yield ()

end HelloWorld