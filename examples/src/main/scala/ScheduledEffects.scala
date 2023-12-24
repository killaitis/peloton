
import peloton.scheduling.cron.CronScheduler
import peloton.scheduling.cron.CronScheduler.syntax.*

import cats.effect.{IO, IOApp}

import scala.concurrent.duration.*


object ScheduledEffects extends IOApp.Simple:
  
  // Here we have two effects. They evaluate to different types, but the result 
  // will be ignored anyway when executed in the background.
  val effectA: IO[Unit] = IO.println("Hello from effect A!")
  val effectB: IO[Int]  = IO.println("Hello from effect B!") >> IO.pure(42)

  def run: IO[Unit] = 
    for
      _  <- IO.println("No background effects have been scheduled yet!")

            // The scheduling API requires the existence of a given instance of a scheduler.
            // Like with ActorSystems, this is achieved by using a resource bracket with a 
            // given scheduler as its lambda parameter. 
            // 
            // Effects that are scheduled within this resource bracket will be terminated when
            // the scheduler is released at the end of the bracket. 
      _  <- CronScheduler.use: _ ?=> 
              for
                      // Schedule the execution of effectA every 2 seconds
                      // `scheduled` is asynchronous, i.e., it will start the schedule in the 
                      // background and return immediately. It is brought in by a class extension 
                      // for `IO` via the import of `CronScheduler.syntax.*`.
                _  <- effectA.scheduled("*/2 * * ? * *")

                      // Schedule the execution of effectB every 5 seconds
                _  <- effectB.scheduled("*/5 * * ? * *")

                      // Sleep for 30 seconds. After this, 
                      // - effectA should be executed roughly 15 times
                      // - effectB should be executed roughly 6 times
                      // 'Roughly' because unlike sleep, the CRON scheduler is fired at full seconds 
                      // which might not fully match the sleep period.
                _  <- IO.sleep(30.seconds)
              yield ()

      _  <- IO.println("No background effects should be running by now!")
    yield ()

end ScheduledEffects
