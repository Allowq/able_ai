package ru.able.camera.camera.graph.factory

import akka.NotUsed
import akka.actor.Cancellable
import akka.stream.scaladsl.Source
import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacv.Frame
import ru.able.camera.camera.graph.CameraReaderGraph
import ru.able.camera.camera.graph.CameraReaderGraph.CameraSource
import ru.able.camera.camera.reader.KillSwitches.GlobalKillSwitch

/**
  * Class for creating CameraSource instances
  * @param cameraSource akka-stream source
  * @param tickingSource akka-stream tick source
  */
class CameraReaderGraphFactory @Inject()(cameraSource: Source[Frame, NotUsed],
                                         tickingSource: Source[Int, Cancellable]) extends LazyLogging {

  /**
    * Creates a new CameraSource instance
    * @param killSwitch A SharedKillswitch to stop the source and as well as the whole stream
    * @return a new CameraSource instance
    */
  def create(gks: GlobalKillSwitch): CameraSource = {
    logger.info("Creating CameraSource")
    // TODO wrap killswitch into a domain object and drop asINstanceof
    new CameraReaderGraph(cameraSource, tickingSource, gks.sharedKillSwitch).createGraph()
  }

}
