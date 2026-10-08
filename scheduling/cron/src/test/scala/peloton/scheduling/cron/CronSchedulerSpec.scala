package peloton.scheduling.cron

import peloton.actor.ActorSystem
import peloton.scheduling.cron.CronScheduler.syntax.*

import peloton.actors.CounterActor

import cats.effect.IO
import cats.effect.Deferred
import cats.effect.Ref
import cats.effect.testing.scalatest.AsyncIOSpec

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*


class CronSchedulerSpec
    extends AsyncFlatSpec 
      with AsyncIOSpec 
      with Matchers:

  behavior of "A CronScheduler"

  it should "trigger the evaluation of an effect according to a given CRON expression" in:
      ActorSystem.use: _ ?=> 
        CronScheduler.use: _ ?=> 
          for
            actor  <- CounterActor.spawn
            _      <- (actor ! CounterActor.Inc).scheduled("* * * ? * *")
            _      <- IO.sleep(12.seconds)

            // Due to the unpredictable nature of scheduling (delays, initialization overhead, inprecise timing, 
            // absolute second precision, etc.), you cannot assume an absolute number of CRON events that have 
            // been scheduled and therefore messages sent to the actor. Thus, we simply test if a reasonable 
            // amount of counter messages have been received by the actor.
            _      <- (actor ? CounterActor.Get).asserting(_ should be >= 10)
          yield ()

  it should "consider a given date range" in:
    pending

  it should "consider a given timezone" in:
    pending

  it should "cancel a scheduled task and stop further executions" in:
    CronScheduler.use: scheduler ?=>
      for
        count      <- Ref.of[IO, Int](0)
        firstTick  <- Deferred[IO, Unit]
        task       <- scheduler.scheduleTask(
                        count.updateAndGet(_ + 1).flatMap:
                          case 1 => firstTick.complete(()).void
                          case _ => IO.unit,
                        cron = "* * * ? * *"
                      )
        _          <- firstTick.get.timeout(3.seconds)
        _          <- task.cancel
        countAtCancel <- count.get
        _          <- IO.sleep(1200.millis)
        countAfter <- count.get
        _           = countAfter shouldBe countAtCancel
      yield ()

  it should "route scheduled effect errors to onError" in:
    CronScheduler.use: scheduler ?=>
      val expectedError = IllegalStateException("scheduled effect failed")

      for
        observedError <- Deferred[IO, Throwable]
        task          <- scheduler.scheduleTask(
                           IO.raiseError[Unit](expectedError),
                           cron = "* * * ? * *",
                           onError = error => observedError.complete(error).void
                         )
        actualError   <- observedError.get.timeout(3.seconds)
        _              = actualError shouldBe expectedError
        _             <- task.cancel
      yield ()

end CronSchedulerSpec