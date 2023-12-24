
import peloton.scheduling.cron.CronScheduler
import peloton.scheduling.cron.CronScheduler.syntax.*
import peloton.actor.ActorSystem

import cats.effect.{IO, IOApp}

import scala.concurrent.duration.*


object ScheduledActor extends IOApp.Simple:
  
  def run: IO[Unit] = 
    ActorSystem.use: _ ?=>
      // The scheduling API requires the existence of a given instance of a scheduler.
      // Like with ActorSystems, this is achieved by using a resource bracket with a 
      // given scheduler as its lambda parameter. 
      // 
      // Effects that are scheduled within this resource bracket will be terminated when
      // the scheduler is released at the end of the bracket. 
      CronScheduler.use: _ ?=> 
        for
                        // Spawn a new HelloWorld actor (see HelloWorld.scala for more details)
          helloActor <- HelloActor.spawn()

                        // Send a message to the actor every 5 seconds. 
                        // `scheduled` is asynchronous, i.e., it will start the schedule in the 
                        // background and return immediately. It is brought in by a class extension 
                        // for `IO` via the import of `CronScheduler.syntax.*`.
          _          <- (helloActor ! HelloActor.Message.Hello("Hello, World!"))
                          .scheduled("*/5 * * ? * *")

                        // sleep for 30 seconds. After this, the helloActor should have received 
                        // roughly 6 Hello messages.
                        // 'Roughly' because unlike sleep, the CRON scheduler is fired at full seconds 
                        // which might not fully match the sleep period.
          _          <- IO.sleep(30.seconds)
        yield ()

end ScheduledActor
