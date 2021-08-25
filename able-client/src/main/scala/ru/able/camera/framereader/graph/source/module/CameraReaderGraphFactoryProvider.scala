package ru.able.camera.framereader.graph.source.module

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Provides
import com.google.inject.Singleton
import com.typesafe.scalalogging.LazyLogging
import ru.able.camera.utils.settings.Settings
import ru.able.camera.framereader.graph.source.framegrabber.FrameGrabberBuilder
import ru.able.camera.framereader.graph.source.CameraReaderGraphFactory

@Provides @Singleton
class CameraReaderGraphFactoryProvider @Inject()(settings: Settings, frameGrabberBuilder: FrameGrabberBuilder)
  extends Provider[CameraReaderGraphFactory] with LazyLogging
{
  override def get(): CameraReaderGraphFactory = new CameraReaderGraphFactory(settings, frameGrabberBuilder)
}
