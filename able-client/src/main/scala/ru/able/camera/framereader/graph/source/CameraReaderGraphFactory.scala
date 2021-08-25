package ru.able.camera.framereader.graph.source

import akka.stream.scaladsl.Source
import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import ru.able.camera.framereader.graph.source.CameraReaderGraph.{CameraSource, RawCameraSource}
import ru.able.camera.framereader.graph.source.framegrabber.FrameGrabberBuilder
import ru.able.camera.utils.settings.Settings
import ru.able.router.model.Orchestrator.GlobalKillSwitch

class CameraReaderGraphFactory @Inject()(settings: Settings, frameGrabberBuilder: FrameGrabberBuilder) extends LazyLogging
{
  /**
    * Creates a new CameraSource instance
    * @param gks A SharedKillswitch to stop the source and as well as the whole stream
    * @return a new CameraSource instance
    */
  def create(gks: GlobalKillSwitch): CameraSource = {
    new CameraReaderGraph(createCameraSource, settings, gks.sharedKillSwitch).createGraph()
  }

  private def createCameraSource: RawCameraSource = {
    val grabber = frameGrabberBuilder.create()
    Source.fromGraph(new CameraReaderStage(grabber))
  }
}
