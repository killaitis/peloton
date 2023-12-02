package peloton.actor

import cats.effect.*
import scala.concurrent.duration.Duration

import peloton.actor.Actor.CanAsk
import scala.reflect.ClassTag

trait ActorRef[M]:

  def classTag: ClassTag[M]

  def tell(message: M): IO[Unit]
  def ask[M2 <: M, R](message: M2, timeout: Duration = Duration.Inf)(using CanAsk[M2, R]): IO[R]
  def terminate: IO[Unit]

  inline def ! (message: M): IO[Unit] = tell(message)
  inline def ? [M2 <: M, R](message: M2, timeout: Duration = Duration.Inf)(using CanAsk[M2, R]): IO[R] = ask[M2, R](message, timeout)
end ActorRef

// object ActorRef:
//   def apply[M](name: String): IO[ActorRef[M]] = ???
