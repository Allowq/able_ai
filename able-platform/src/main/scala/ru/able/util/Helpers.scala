package ru.able.util

import java.util.{Timer, TimerTask}
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Cancellable}
import akka.util.Timeout

import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.concurrent.duration.Duration

object Helpers {
  def runAfterDelay[T](delayMILLS: Long)
                      (f: => T)
                      (implicit system: ActorSystem, ec: ExecutionContext)
  : Cancellable =
  {
    system.scheduler.scheduleOnce(Duration(delayMILLS, TimeUnit.MILLISECONDS))(f)(ec)
  }

  def runAfterDelay(timeout: Timeout, timer: Timer)
                   (f: () => Unit)
  : Unit =
  {
    timer.schedule(new TimerTask() { def run: Unit = f() }, timeout.duration.toMillis)
  }

  def runAfterDelay(delayMills: Long, timer: Timer)
                   (f: () => Unit)
  : Unit =
  {
    timer.schedule(new TimerTask() { def run: Unit = f() }, delayMills)
  }

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

  def measureTime[T](f: => T)
  : T =
  {
    val s = System.nanoTime
    val ret = f
    println("time: "+(System.nanoTime-s)/1e6+"ms")
    ret
  }
}
