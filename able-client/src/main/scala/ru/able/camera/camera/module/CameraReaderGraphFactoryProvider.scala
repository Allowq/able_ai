package ru.able.camera.camera.module

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

import akka.stream.scaladsl.Source
import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Provides
import com.google.inject.Singleton
import ru.able.camera.camera.stage.CameraReaderStage
import ru.able.camera.framegrabber.FrameGrabberBuilder
import ru.able.camera.utils.settings.Settings

import scala.concurrent.duration.FiniteDuration
import CameraReaderGraphFactoryProvider._
import com.typesafe.scalalogging.LazyLogging
import ru.able.camera.camera.graph.factory.CameraReaderGraphFactory

object CameraReaderGraphFactoryProvider {
  private val oneSecInMillis = 1000
}

@Provides @Singleton
class CameraReaderGraphFactoryProvider @Inject()(settings: Settings, frameGrabberBuilder: FrameGrabberBuilder)
  extends Provider[CameraReaderGraphFactory] with LazyLogging
{
  override def get(): CameraReaderGraphFactory = new CameraReaderGraphFactory(createCameraSource, createTickingSource)

  private def createCameraSource = {
    logger.debug("Creating Grabber Source")
    val grabber = frameGrabberBuilder.create()
    Source.fromGraph(new CameraReaderStage(grabber))
  }

  private def createTickingSource = {
    logger.debug("Creating Ticking Source")
    val initialDelay = settings.getDuration("camera.initialDelay", SECONDS)
    val cameraFPS    = settings.getInt("camera.fps")
    val startValue   = 0
    val tickInterval = FiniteDuration(oneSecInMillis / cameraFPS, MILLISECONDS)

    Source.tick(initialDelay, tickInterval, startValue)
  }
}
