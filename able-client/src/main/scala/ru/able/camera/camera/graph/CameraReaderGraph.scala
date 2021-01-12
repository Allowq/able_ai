package ru.able.camera.camera.graph

import akka.NotUsed
import akka.stream.{FlowShape, SharedKillSwitch, SourceShape, ThrottleMode}
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacv.Frame
import ru.able.camera.camera.CameraFrame
import ru.able.camera.camera.graph.CameraReaderGraph.CameraSource
import ru.able.camera.camera.graph.CameraReaderGraph.RawCameraSource
import ru.able.camera.utils.MediaConversion
import ru.able.camera.utils.settings.Settings

import scala.concurrent.duration._

object CameraReaderGraph {
  type CameraSource    = Source[CameraFrame, NotUsed]
  type RawCameraSource = Source[Frame, NotUsed]
  type FrameFlow       = Flow[CameraFrame, CameraFrame, NotUsed]
}

class CameraReaderGraph(rawCameraSource: RawCameraSource,
                        settings: Settings,
                        killSwitch: SharedKillSwitch) extends LazyLogging
{
  def createGraph(): CameraSource = Source.fromGraph(GraphDSL.create() {
    implicit builder => {
      import GraphDSL.Implicits._

      val cameraStream: PortOps[Frame] = builder
        .add(rawCameraSource)
        .out
        .via(killSwitch.flow)
        .throttle(settings.getInt("camera.fps"), 1.second)
//        .throttle(10, 1.second, 10 * 30, ThrottleMode.Shaping)

      val ImgMatConverter: FlowShape[Frame, CameraFrame] = builder
        .add(Flow[Frame]
          .via(killSwitch.flow)
          .map(MediaConversion.toMat(_))
          .map(CameraFrame(_))
        )

      val stream = cameraStream ~> ImgMatConverter
      SourceShape(stream.outlet)
    }
  })
}
