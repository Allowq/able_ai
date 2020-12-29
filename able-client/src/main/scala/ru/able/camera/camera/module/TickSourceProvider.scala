package ru.able.camera.camera.module

import java.util.concurrent.TimeUnit.{MILLISECONDS, SECONDS}

import akka.stream.scaladsl.Source
import com.google.inject.{Inject, Provider}
import com.typesafe.scalalogging.LazyLogging
import ru.TickSource
import ru.able.camera.camera.module.TickSourceProvider.oneSecInMillis
import ru.able.camera.utils.settings.Settings

import scala.concurrent.duration.FiniteDuration
@deprecated
object TickSourceProvider {
  private val oneSecInMillis = 1000
}
@deprecated
class TickSourceProvider @Inject()(settings: Settings)
    extends Provider[TickSource] with LazyLogging {

  override def get(): TickSource = {
    println("What is up dear brother of mine?")
    val initialDelay = settings.getDuration("camera.initialDelay", SECONDS)
    val cameraFPS    = settings.getInt("camera.fps")

    val tickInterval =
      FiniteDuration(oneSecInMillis / cameraFPS, MILLISECONDS)

    logger.info(s"Tick interval: $tickInterval")

    Source.tick(initialDelay, tickInterval, 0)
  }

}
