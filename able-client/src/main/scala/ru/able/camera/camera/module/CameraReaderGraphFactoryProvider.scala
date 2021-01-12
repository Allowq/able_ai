package ru.able.camera.camera.module

import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

import akka.stream.scaladsl.Source
import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Provides
import com.google.inject.Singleton
import com.typesafe.scalalogging.LazyLogging

import ru.able.camera.camera.stage.CameraReaderStage
import ru.able.camera.framegrabber.FrameGrabberBuilder
import ru.able.camera.utils.settings.Settings
import ru.able.camera.camera.graph.factory.CameraReaderGraphFactory

@Provides @Singleton
class CameraReaderGraphFactoryProvider @Inject()(settings: Settings, frameGrabberBuilder: FrameGrabberBuilder)
  extends Provider[CameraReaderGraphFactory] with LazyLogging
{
  override def get(): CameraReaderGraphFactory = new CameraReaderGraphFactory(createCameraSource, settings)

  private def createCameraSource = {
    logger.debug("Creating Grabber Source")
    val grabber = frameGrabberBuilder.create()
    Source.fromGraph(new CameraReaderStage(grabber))
  }
}
