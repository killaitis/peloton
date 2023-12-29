package peloton.scheduling.cron

import peloton.actor.ActorSystem
import peloton.scheduling.cron.CronScheduler.syntax.*

import peloton.actors.CounterActor

import cats.effect.IO
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

end CronSchedulerSpec