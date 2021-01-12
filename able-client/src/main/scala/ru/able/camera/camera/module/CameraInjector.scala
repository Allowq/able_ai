package ru.able.camera.camera.module

import com.google.inject.AbstractModule
import com.google.inject.name.Names

import ru.able.camera.camera.graph.factory.CameraReaderGraphFactory
import ru.able.camera.framegrabber.{FFmpegFrameGrabberBuilder, FrameGrabberBuilder}

class CameraInjector extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[CameraReaderGraphFactory])
      .annotatedWith(Names.named("CameraReaderFactory"))
      .toProvider(classOf[CameraReaderGraphFactoryProvider])

    bind(classOf[FrameGrabberBuilder])
      .to(classOf[FFmpegFrameGrabberBuilder])
  }
}
