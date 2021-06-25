package ru.able.util

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Cancellable}

import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.concurrent.duration.Duration

object Helpers {
  // timeout: TimeUnit.MILLISECONDS
  def runWithTimeout[T](timeout: Long)
                       (f: => T)
                       (implicit ec: ExecutionContext)
  : Option[T] =
  {
    try {
      Some(Await.result(Future(f), Duration(timeout, TimeUnit.MILLISECONDS)))
    } catch {
      case _: TimeoutException => None
    }
  }

  def runAfterDelay[T](delay: Long)
                      (f: => T)
                      (implicit system: ActorSystem, ec: ExecutionContext)
  : Cancellable =
  {
    system.scheduler.scheduleOnce(Duration(delay, TimeUnit.MILLISECONDS))(f)(ec)
  }
}
