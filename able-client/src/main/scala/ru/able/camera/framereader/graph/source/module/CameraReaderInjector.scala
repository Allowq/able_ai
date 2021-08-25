package ru.able.camera.framereader.graph.source.module

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import ru.able.camera.framereader.graph.source.framegrabber.{FFmpegFrameGrabberBuilder, FrameGrabberBuilder}
import ru.able.camera.framereader.graph.source.CameraReaderGraphFactory

class CameraReaderInjector extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[FrameGrabberBuilder])
      .to(classOf[FFmpegFrameGrabberBuilder])

    bind(classOf[CameraReaderGraphFactory])
      .annotatedWith(Names.named("CameraReaderFactory"))
      .toProvider(classOf[CameraReaderGraphFactoryProvider])
  }
}
