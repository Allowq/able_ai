package ru.able.util

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Cancellable}

import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.concurrent.duration.Duration

object Helpers {
  def runWithTimeout[T](timeoutMILLS: Long)
                       (f: => T)
                       (implicit ec: ExecutionContext)
  : Option[T] =
  {
    try {
      Some(Await.result(Future(f), Duration(timeoutMILLS, TimeUnit.MILLISECONDS)))
    } catch {
      case _: TimeoutException => None
    }
  }

  def runAfterDelay[T](delayMILLS: Long)
                      (f: => T)
                      (implicit system: ActorSystem, ec: ExecutionContext)
  : Cancellable =
  {
    system.scheduler.scheduleOnce(Duration(delayMILLS, TimeUnit.MILLISECONDS))(f)(ec)
  }
}
