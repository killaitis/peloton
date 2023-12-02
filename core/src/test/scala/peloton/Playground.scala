package peloton

import peloton.actor.ActorSystem
// import cats.syntax.all.*
import cats.effect.IOApp
import cats.effect.IO
import scala.concurrent.duration.*

object Playground extends IOApp.Simple:

  def run: IO[Unit] = 
    ActorSystem().use: system => 
      for
        _        <- IO.println("Start using ActorSystem")

        actorRef <- system.spawn[Double, String](
                      name = "actor a",
                      initialState = 0.0, 
                      initialBehavior = (s, m, ctx) => m match {
                        case s => IO.println(s"got msg '$s'") >> IO.pure(ctx.currentBehavior)
                      }
                    )
        ref2     <- system.actorRef[Int]("actor a")
        _        <- actorRef ! "Foo"
        // _        <- ref2.traverse_(_ ! "Bar")
        _        <- IO.println("Sleeping before shutdown ...")
        _        <- IO.sleep(1.second)
        _        <- IO.println("End using ActorSystem")
      yield ()
