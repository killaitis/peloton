package peloton.actor.internal

import peloton.actor.Actor

import cats.effect.Deferred
import cats.effect.FiberIO
import cats.effect.IO
import cats.effect.Ref
import cats.effect.std.Mutex
import cats.effect.std.Queue
import cats.effect.std.Semaphore
import cats.implicits.*

import scala.concurrent.duration.*
import java.util.UUID

/**
  * Type of the response channel to transport a possible actor response back to the caller. 
  * 
  * We use a [[Deferred]] here where the consumer (the client) will listen and wait while the 
  * producer (the actor) will at some point in time send a response. The response is
  * either the actor's response or an error (`Throwable`). The `Deferred` 
  * is finally wrapped into an `Option`. This allows it to skip the whole creation of a
  * `Deferred` when no response is needed (`tell()`) and only allocate it if needed (`ask()`)
  */
private [internal] type ActorResponseChannel = Deferred[IO, Either[Throwable, Any]]

/**
   * An entry in an actor's inbox or stash.
   *
   * @param message the message delivered to the actor
   * @param responseChannel channel used to complete an ASK request, if present
   * @param releaseMailboxSlot releases an external inbox-capacity slot when the entry is removed from the inbox
  */
private[internal] final case class ActorMessage[M](
  message: M,
  responseChannel: Option[ActorResponseChannel],
  releaseMailboxSlot: IO[Unit] = IO.unit
)

private[internal] object ActorMailbox:
  /** Creates an unbounded inbox or an inbox with a semaphore-backed external capacity. */
  def create[M](capacity: Option[Int]): IO[(Queue[IO, ActorMessage[M]], Option[Semaphore[IO]])] =
    capacity match
      case None =>
        Queue.unbounded[IO, ActorMessage[M]].map(_ -> None)
      case Some(value) if value > 0 =>
        (Queue.unbounded[IO, ActorMessage[M]], Semaphore[IO](value.toLong)).mapN:
          case (queue, semaphore) => (queue, Some(semaphore))
      case Some(_) =>
        IO.raiseError(IllegalArgumentException("inboxCapacity must be greater than zero"))

  /** Enqueues an external message, backpressuring until a configured capacity slot is available. */
  def enqueue[M](
    lifecycle: ActorLifecycle,
    inbox: Queue[IO, ActorMessage[M]],
    capacity: Option[Semaphore[IO]],
    message: M,
    responseChannel: Option[ActorResponseChannel]
  ): IO[Unit] =
    capacity match
      case None => lifecycle.guard(inbox.offer(ActorMessage(message, responseChannel)))
      case Some(semaphore) =>
        semaphore.acquire >>
          lifecycle.guard(
            inbox.offer(ActorMessage(message, responseChannel, semaphore.release))
          ).guaranteeCase:
            case cats.effect.Outcome.Succeeded(_) => IO.unit
            case _                                => semaphore.release

/** Serializes message admission with actor termination. */
private[internal] final class ActorLifecycle private (
  mutex: Mutex[IO],
  acceptingMessages: Ref[IO, Boolean]
):
  /** Runs an operation only while the actor is accepting messages. */
  def guard[A](effect: IO[A]): IO[A] =
    mutex.lock.surround:
      acceptingMessages.get.flatMap:
        case true  => effect
        case false => IO.raiseError(Actor.ActorTerminatedException())

        /** Atomically stops admission and runs the actor's termination action once. */
  def terminate(stop: IO[Unit]): IO[Unit] =
    mutex.lock.surround:
      acceptingMessages.modify(accepting => (false, accepting)).flatMap:
        case true  => stop
        case false => IO.unit

private[internal] object ActorLifecycle:
  /** Creates an accepting lifecycle gate guarded by the actor's queue mutex. */
  def create(mutex: Mutex[IO]): IO[ActorLifecycle] =
    Ref.of[IO, Boolean](true).map(new ActorLifecycle(mutex, _))

  /** Fails queued and stashed ASK requests and releases any associated inbox slots. */
  def failPending[M](queues: Queue[IO, ActorMessage[M]]*): IO[Unit] =
    queues.toList.traverseVoid: queue =>
      queue.tryTakeN(None).flatMap:
        _.traverseVoid: message =>
          message.releaseMailboxSlot >>
            message.responseChannel.traverseVoid(_.complete(Left(Actor.ActorTerminatedException())).void)

/** Tracks delayed self-message fibers so actor termination can cancel outstanding timers. */
private[internal] final class ActorTimerRegistry private (
  fibers: Ref[IO, Map[UUID, FiberIO[Unit]]]
):
  /** Starts a delayed effect and removes its fiber from the registry when it completes. */
  def scheduleOnce(delay: FiniteDuration, effect: IO[Unit]): IO[FiberIO[Unit]] =
    if delay < Duration.Zero then
      IO.raiseError(IllegalArgumentException("timer delay must not be negative"))
    else
      for
        id  <- IO(UUID.randomUUID())
        gate <- Deferred[IO, Unit]
        fiber <- (gate.get >> IO.sleep(delay) >> effect)
                   .guarantee(fibers.update(_ - id))
                   .start
        _ <- fibers.update(_ + (id -> fiber))
        _ <- gate.complete(())
      yield fiber

  /** Cancels every timer still registered. */
  def cancelAll: IO[Unit] =
    fibers.getAndSet(Map.empty).flatMap(_.values.toList.traverseVoid(_.cancel))

private[internal] object ActorTimerRegistry:
  /** Creates an empty timer registry. */
  def create: IO[ActorTimerRegistry] =
    Ref.of[IO, Map[UUID, FiberIO[Unit]]](Map.empty).map(new ActorTimerRegistry(_))
