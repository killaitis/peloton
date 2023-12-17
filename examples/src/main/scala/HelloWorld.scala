import peloton.actor.ActorSystem
import peloton.actor.Actor.*

import cats.effect.IOApp
import cats.effect.IO

object HelloActor:

  sealed trait Message

  object Message:
    case class Hello(greeting: String) extends Message
    case object HowAreYou extends Message

    final case class HowAreYouResponse(msg: String)
    given CanAsk[HowAreYou.type, HowAreYouResponse] = canAsk

  def spawn()(using actorSystem: ActorSystem) = 
    actorSystem.spawn[Unit, Message](
      name            = "HelloActor",
      initialState    = (),
      initialBehavior = (_, message, context) => message match
        case Message.Hello(greeting) => IO.println(greeting) >> context.currentBehaviorM
        case Message.HowAreYou       => context.respond(Message.HowAreYouResponse("I'm fine"))
    )
end HelloActor

object HelloWorld extends IOApp.Simple:

  import HelloActor.Message.given

  def run: IO[Unit] = 
    ActorSystem.use: _ ?=> 
      for
        helloActor <- HelloActor.spawn()
        _          <- helloActor ! HelloActor.Message.Hello("Hello, World!")
        response   <- helloActor ? HelloActor.Message.HowAreYou
        _          <- IO.println(s"The Hello actor says: $response")
      yield ()

end HelloWorld