package ru.able.camera.camera.graph.factory

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacv.Frame

import ru.able.camera.camera.graph.CameraReaderGraph
import ru.able.camera.camera.graph.CameraReaderGraph.CameraSource
import ru.able.common.Switches.GlobalKillSwitch
import ru.able.camera.utils.settings.Settings

class CameraReaderGraphFactory @Inject()(cameraSource: Source[Frame, NotUsed],
                                         settings: Settings) extends LazyLogging
{
  /**
    * Creates a new CameraSource instance
    * @param gks A SharedKillswitch to stop the source and as well as the whole stream
    * @return a new CameraSource instance
    */
  def create(gks: GlobalKillSwitch): CameraSource = {
    logger.info("Creating CameraSource")
    // TODO wrap killswitch into a domain object and drop asINstanceof
    new CameraReaderGraph(cameraSource, settings, gks.sharedKillSwitch).createGraph()
  }
}
