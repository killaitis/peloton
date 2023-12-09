package peloton.actor

import cats.effect.*
import scala.concurrent.duration.FiniteDuration

import peloton.actor.Actor.CanAsk
import scala.reflect.ClassTag

trait ActorRef[M]:

  def classTag: ClassTag[M] // TODO: this is pretty bad and should be replaced (perhaps with scala.quoted.Type)

  def tell(message: M): IO[Unit]
  def ask[M2 <: M, R](message: M2, timeout: FiniteDuration = Actor.DefaultTimeout)(using CanAsk[M2, R]): IO[R]
  def terminate: IO[Unit]

  inline def ! (message: M): IO[Unit] = tell(message)
  inline def ? [M2 <: M, R](message: M2, timeout: FiniteDuration = Actor.DefaultTimeout)(using CanAsk[M2, R]): IO[R] = ask[M2, R](message, timeout)
end ActorRef
