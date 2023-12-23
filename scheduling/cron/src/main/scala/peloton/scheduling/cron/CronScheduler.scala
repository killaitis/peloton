package peloton.scheduling.cron

import cats.effect.*
import cats.effect.std.{Queue, *}

import org.quartz.CronScheduleBuilder.*
import org.quartz.JobBuilder.*
import org.quartz.TriggerBuilder.*
import org.quartz.impl.StdSchedulerFactory
import org.quartz.{JobDataMap, JobExecutionContext, JobKey, Scheduler, TriggerKey}

import java.time.ZoneId
import java.util.{TimeZone, UUID}


final case class CronScheduler private (
  private val scheduler: Scheduler, 
  private val dispatcher: Dispatcher[IO]
):

  import CronScheduler.*

  /**
    * Starts a given effect whenever the current time is matching a specific CRON expression
    * 
    * The scheduling is implemented using a producer-consumer pattern. Both the producer and 
    * the consumer use a shared Cats Effect event queue to communicate.
    * 
    * The producer is a Quartz job that is registered with a unique name for the given 
    * CRON expression. It will push an event into the shared event queue.
    * 
    * The consumer is an endless loop that will wait for an event in the shared event queue,
    * take it and execute the given effect.
    *
    * @param cron 
    *   A valid Quartz CRON expression
    * @param timezone
    *   A Java [[TimeZone]]. This is used by Quartz and will affect the evaluation of the CRON expression
    * @param effect
    *   An effect that is executed when Quartz fires a CRON event
    * @return
    *   Unit
    */
  def schedule[A](cron: String, timezone: TimeZone, effect: IO[A]): IO[Unit] = 
    for
      // Create a shared event queue.
      eventQueue   <- Queue.unbounded[IO, Unit]

      // Create a Quartz job that pushes events to the event queue
      _            <- IO.blocking:
                        val jobKey     = JobKey.jobKey("scheduled-effect")
                        val triggerKey = TriggerKey.triggerKey(s"scheduled-effect-${UUID.randomUUID().toString()}")

                        val job = 
                          newJob(classOf[PublishJob])
                            .withIdentity(jobKey)
                            .requestRecovery()
                            .storeDurably()
                            .build()

                        scheduler.addJob(job, true)

                        // Parameters for Quartz jobs are passed via JobDataMaps. Here we pass the event queue 
                        // and the dispatcher to the job.
                        val jobDataMap = new JobDataMap()
                        jobDataMap.put(PublishJobDataKey, PublishJobData(eventQueue, dispatcher))

                        val trigger = 
                          newTrigger()
                            .withIdentity(triggerKey)
                            .forJob(jobKey)
                            .usingJobData(jobDataMap)
                            .withSchedule(cronSchedule(cron).inTimeZone(timezone))
                            .startNow()
                            .build()

                        scheduler.scheduleJob(trigger)

      // Create an endless consumer loop that takes a single event from the queue and runs the given effect
      consumerLoop  = (eventQueue.take >> effect.attempt).foreverM

      // Start the consumer loop asynchronously in the background
      _            <- consumerLoop.start
    yield ()

end CronScheduler


object CronScheduler:
  
  private val PublishJobDataKey = "jobdata"
  
  private case class PublishJobData(
    eventQueue: Queue[IO, Unit],
    dispatcher: Dispatcher[IO]
  )

  private class PublishJob extends org.quartz.Job:
    override def execute(context: JobExecutionContext): Unit =  
      // Extract event queue and dispatcher from the Quartz job data
      val PublishJobData(eventQueue, dispatcher) = 
        context
          .getTrigger()
          .getJobDataMap()
          .get(PublishJobDataKey)
          .asInstanceOf[PublishJobData]

      // All this Quartz job has to do is to publish a single event to the shared event queue. 
      // The event queue is implemented using a Cats Effect Queue, so publishing to the queue 
      // is regarded as an effect and we have to run it on the provided effect dispatcher. 
      // The type or values of the published event itself does not matter to the consumer because 
      // the consumer just listens for any event and does not evaluate it, so we can just send 
      // an instance of Unit.
      dispatcher.unsafeRunSync(eventQueue.offer(()))
    end execute
  end PublishJob

  /**
    * Creates a resource bracket for a [[CronScheduler]] resource. 
    * 
    * Within the lifetime of this resource, the scheduler can be used to asynchronously start effects by Quartz 
    * cron expresiions. After the scheduler resource is released, all cron jobs started by this scheduler will
    * be stopped and also released.
    * 
    * The following example will print a message every 10 seconds for 5 minutes:
    * {{{
    *   CronScheduler.make.use { case given CronScheduler => 
    *     for {     
    *       _ <- IO.println("Hello out there!").scheduled("*\/10 * * ? * *")
    *       _ <- IO.sleep(5.minutes)
    *     } yield ()
    *   }
    * }}}
    * 
    * @return 
    *   the [[CronScheduler]] resource
    */
  def make: Resource[IO, CronScheduler] =
    Dispatcher
      .parallel[IO](await = false)
      .flatMap(dispatcher => 
        Resource.make(
          acquire = 
            IO.blocking:
              val scheduler = StdSchedulerFactory.getDefaultScheduler()
              scheduler.start()
              CronScheduler(scheduler, dispatcher)
        )(
          release = scheduler => IO.blocking(scheduler.scheduler.shutdown())
        )
      )
  end make

  def use[A](f: CronScheduler ?=> IO[A]): IO[A] = 
    CronScheduler.make.use { case given CronScheduler => f }
    
  object syntax:

    /**
      * Class extension that adds the `scheduled` syntax to a given effect
      *
      * @param ioa
      *   An effect that will be started by the CRON scheduler and executed in the background.
      */
    extension (ioa: IO[?])

      /**
        * Schedules an effect by evaluating a CRON expression.
        *
        * @param cron
        *   A valid Quartz CRON expression 
        * @param timezone
        *   A Java [[TimeZone]]. This will influence the evaluation of the CRON expression
        * @param scheduler
        *   a given [[CronScheduler]] which will be used to schedule the effect
        * @return
        *   As scheduling an effect is also an effect, this function returns an effect in `Unit`
        */
      def scheduled(cron: String, timezone: TimeZone = TimeZone.getTimeZone(ZoneId.systemDefault()))(using scheduler: CronScheduler): IO[Unit] = 
        scheduler.schedule(cron, timezone, ioa)
    
  end syntax

end CronScheduler