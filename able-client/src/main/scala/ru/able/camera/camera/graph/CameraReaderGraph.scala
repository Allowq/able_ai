package ru.able.camera.camera.graph

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.Outlet
import akka.stream.SharedKillSwitch
import akka.stream.SourceShape
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.OpenCVFrameConverter
import ru.TickSource
import ru.able.camera.camera.CameraFrame
import ru.able.camera.camera.graph.CameraReaderGraph.CameraSource
import ru.able.camera.camera.graph.CameraReaderGraph.RawCameraSource
import ru.able.graph.GraphFactory

object CameraReaderGraph {
  type CameraSource    = Source[CameraFrame, NotUsed]
  type RawCameraSource = Source[Frame, NotUsed]
  type FrameFlow       = Flow[CameraFrame, CameraFrame, NotUsed]
}

/**
  * Creates a video stream source from camera
  * @param rawCameraSource source to read from
  * @param tickingSource dictates the rate of reading from the source
  * @param killSwitch Shared between the whole stream to handle shutdown
  */
class CameraReaderGraph(rawCameraSource: RawCameraSource,
                        tickingSource: TickSource,
                        killSwitch: SharedKillSwitch)
    extends GraphFactory[CameraSource]
    with LazyLogging {

  override def createGraph(): CameraSource =
    Source.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      // todo make it dependency
      val converter = new OpenCVFrameConverter.ToIplImage()

      val IplImageConverter: FlowShape[Frame, CameraFrame] = builder.add(
        Flow[Frame]
          .via(killSwitch.flow)
          .map(converter.convert)
          .map(CameraFrame(_)))

      val Camera: Outlet[Frame] = builder.add(rawCameraSource).out

      val cameraStream = Camera
        .via(killSwitch.flow)
        .zip(tickingSource)
        .map(_._1)

      val stream = cameraStream ~> IplImageConverter

      SourceShape(stream.outlet)
    })
}
