package peloton.scheduling.cron

import cats.effect.*
import cats.effect.std.{Queue, *}
import cats.implicits.*

import org.quartz.CronScheduleBuilder.*
import org.quartz.JobBuilder.*
import org.quartz.TriggerBuilder.*
import org.quartz.impl.StdSchedulerFactory
import org.quartz.{JobDataMap, JobExecutionContext, JobKey, Scheduler, TriggerKey}

import java.util.{Date, TimeZone, UUID}

/** Handle for a scheduled CRON task and its background consumer. */
final class ScheduledTask private[cron] (
  triggerKey: TriggerKey,
  scheduler: Scheduler,
  fiber: FiberIO[Unit],
  tasks: Ref[IO, Map[TriggerKey, ScheduledTask]]
):
  def cancel: IO[Unit] =
    IO.blocking(scheduler.unscheduleJob(triggerKey)).void
      .guarantee(fiber.cancel >> tasks.update(_ - triggerKey))

  def join: IO[OutcomeIO[Unit]] = fiber.join

final case class CronScheduler private (
  private val scheduler: Scheduler,
  private val dispatcher: Dispatcher[IO],
  private val tasks: Ref[IO, Map[TriggerKey, ScheduledTask]]
):

  import CronScheduler.*

  def schedule[A](effect: IO[A],
                  cron: String,
                  timezone: TimeZone,
                  startDate: Option[Date],
                  endDate: Option[Date]
                 ): IO[Unit] =
    scheduleTask(effect, cron, timezone, startDate, endDate).void

  /**
    * Schedule an effect and return a handle for cancellation and completion observation.
    * Errors from the effect are passed to `onError`; if that handler fails, `join` exposes
    * the failed consumer fiber.
    */
  def scheduleTask[A](effect: IO[A],
                      cron: String,
                      timezone: TimeZone = TimeZone.getDefault,
                      startDate: Option[Date] = None,
                      endDate: Option[Date] = None,
                      onError: Throwable => IO[Unit] = _ => IO.unit
                     ): IO[ScheduledTask] =
    for
      eventQueue <- Queue.unbounded[IO, Unit]
      triggerKey <- IO.blocking:
                      val jobKey = JobKey.jobKey("scheduled-effect")
                      val key = TriggerKey.triggerKey(s"scheduled-effect-${UUID.randomUUID()}")
                      val job =
                        newJob(classOf[PublishJob])
                          .withIdentity(jobKey)
                          .requestRecovery()
                          .storeDurably()
                          .build()

                      scheduler.addJob(job, true)

                      val jobDataMap = new JobDataMap()
                      jobDataMap.put(PublishJobDataKey, PublishJobData(eventQueue, dispatcher))

                      val trigger =
                        newTrigger()
                          .withIdentity(key)
                          .forJob(jobKey)
                          .usingJobData(jobDataMap)
                          .withSchedule(cronSchedule(cron).inTimeZone(timezone))
                          .startAt(startDate.getOrElse(Date()))
                          .endAt(endDate.getOrElse(null))
                          .build()

                      scheduler.scheduleJob(trigger)
                      key
      runEffect = effect.attempt.flatMap:
                    case Right(_)    => IO.unit
                    case Left(error) => onError(error)
      consumer = (eventQueue.take >> runEffect).foreverM *> IO.unit
      fiber <- consumer.start
      task = new ScheduledTask(triggerKey, scheduler, fiber, tasks)
      _ <- tasks.update(_ + (triggerKey -> task))
    yield task

  private def cancelTasks: IO[Unit] =
    tasks.get.flatMap(_.values.toList.traverseVoid(_.cancel))

end CronScheduler

object CronScheduler:

  private val PublishJobDataKey = "jobdata"

  private case class PublishJobData(
    eventQueue: Queue[IO, Unit],
    dispatcher: Dispatcher[IO]
  )

  private class PublishJob extends org.quartz.Job:
    override def execute(context: JobExecutionContext): Unit =
      val PublishJobData(eventQueue, dispatcher) =
        context
          .getTrigger()
          .getJobDataMap()
          .get(PublishJobDataKey)
          .asInstanceOf[PublishJobData]

      dispatcher.unsafeRunSync(eventQueue.offer(()))
    end execute
  end PublishJob

  def make: Resource[IO, CronScheduler] =
    Dispatcher
      .parallel[IO](await = false)
      .flatMap: dispatcher =>
        Resource.make(
          for
            tasks <- Ref.of[IO, Map[TriggerKey, ScheduledTask]](Map.empty)
            scheduler <- IO.blocking:
                           val createdScheduler = StdSchedulerFactory.getDefaultScheduler()
                           createdScheduler.start()
                           createdScheduler
          yield CronScheduler(scheduler, dispatcher, tasks)
        )(cronScheduler =>
          cronScheduler.cancelTasks >> IO.blocking(cronScheduler.scheduler.shutdown())
        )
  end make

  def use[A](f: CronScheduler ?=> IO[A]): IO[A] =
    CronScheduler.make.use { case given CronScheduler => f }

  object syntax:
    extension (ioa: IO[?])
      def scheduled(cron: String,
                    timezone: TimeZone = TimeZone.getDefault,
                    startDate: Option[Date] = None,
                    endDate: Option[Date] = None
                   )(using scheduler: CronScheduler): IO[Unit] =
        scheduler.schedule(ioa, cron, timezone, startDate, endDate)

      def scheduledTask(cron: String,
                        timezone: TimeZone = TimeZone.getDefault,
                        startDate: Option[Date] = None,
                        endDate: Option[Date] = None,
                        onError: Throwable => IO[Unit] = _ => IO.unit
                       )(using scheduler: CronScheduler): IO[ScheduledTask] =
        scheduler.scheduleTask(ioa, cron, timezone, startDate, endDate, onError)
  end syntax

end CronScheduler