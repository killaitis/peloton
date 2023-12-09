package peloton

import cats.effect.IO
import cats.effect.IOApp

import peloton.actor.Actor.*
import peloton.actor.ActorSystem

import java.net.URI
import scala.concurrent.duration.*
import peloton.config.Config

object HelloActor:

  sealed trait Message

  object Message:
    case class Hello(msg: String) extends Message
    case object HowAreYou extends Message

    final case class HowAreYouResponse(msg: String)
    given CanAsk[HowAreYou.type, HowAreYouResponse] = canAsk

  def spawn()(using actorSystem: ActorSystem) = 
    actorSystem.spawn[Unit, Message](
      name = "HelloActor",
      initialState = (),
      initialBehavior = (_, command, context) => command match
        case Message.Hello(msg) => IO.println(msg) >> IO.pure(context.currentBehavior)
        case Message.HowAreYou  => context.respond(Message.HowAreYouResponse("I'm fine"))
    )
end HelloActor


object Playground extends IOApp.Simple:
  def run: IO[Unit] = 
    System.setProperty("peloton.http.hostname", "localhost");
    System.setProperty("peloton.http.port",     "8080");

    for 
      config <- Config.default()
      _      <- ActorSystem.withActorSystem(config) { actorSystem => 
                  for
                    _  <- IO.println("Started using ActorSystem")
                    _  <- HelloActor.spawn()(using actorSystem)
                    
                    helloActor <- actorSystem.remoteActorRef[HelloActor.Message](URI("peloton://localhost:8080/HelloActor"))
                    _          <- helloActor ! HelloActor.Message.Hello("Hello from the Playground!")
                    hay        <- helloActor ? HelloActor.Message.HowAreYou
                    _          <- IO.println(s"The Hello actor's state is '$hay'")

                    _  <- IO.println("Waiting before shutdown ...")
                    _  <- IO.sleep(60.seconds)
                    _  <- IO.println("Ending using ActorSystem")
                  yield ()
                }
    yield ()

end Playground