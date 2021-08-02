package ru.able.services.detector

import java.nio.ByteBuffer

import akka.actor.{Actor, ActorContext, ActorRef, Props}
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Sink}
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_imgproc.{COLOR_BGR2RGB, cvtColor}
import org.platanios.tensorflow.api.{Tensor, _}
import ru.able.server.controllers.flow.protocol.{Command, Event}
import ru.able.services.detector.model.{DetectionOutput, DetectorModel, DetectorViaFileDescription}
import ru.able.services.detector.pipeline.{DetectorStage, ShowSignedFrameStage}
import ru.able.util.Helpers

object DetectorController {

  def apply()(implicit context: ActorContext)
  : ActorRef =
  {
    context.actorOf(Props(new DetectorController()), "DetectorControllerActor")
  }

  def getDetectionFlow[Cmd, Evt](detectController: ActorRef)
  : Flow[Event[Evt], Command[Cmd], Any] =
  {
    Flow.fromGraph[Event[Evt], Command[Cmd], Any] {
      GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._

        val pipelineIn = b.add(new DetectorStage(detectController).async)
        val pipelineOut = b.add(new ShowSignedFrameStage[Command[Cmd]])

        pipelineIn.out0 ~> pipelineOut
        pipelineIn.out1 ~> Sink.ignore

        FlowShape(pipelineIn.in, pipelineOut.out)
      }
    }
  }
}

class DetectorController private (private val _detectorModel: DetectorModel) extends Actor with LazyLogging
{
  // retrieve the output placeholders
  private val _imagePlaceholder = _detectorModel.graph.getOutputByName("image_tensor:0")
  private val _fetches = Seq(
    _detectorModel.graph.getOutputByName("detection_boxes:0"),
    _detectorModel.graph.getOutputByName("detection_scores:0"),
    _detectorModel.graph.getOutputByName("detection_classes:0"),
    _detectorModel.graph.getOutputByName("num_detections:0")
  )

  def this(folderPath: Option[String] = None) {
    this(
      new DetectorViaFileDescription(folderPath).defineDetector()
    )
  }

  override def receive: Receive = {
    case image: Mat => sender ! detect(matToTensor(image))
    case index: Int => sender ! _detectorModel.labelMap.getOrElse(index, "unknown")
    case "getDictionary" => sender ! _detectorModel.labelMap
  }

  // run the object detection model on an image
  private def detect(image: Tensor): DetectionOutput = {
    // Run the detection model
    val Seq(boxes, scores, classes, num) = _detectorModel.tfSession.run(
      fetches = _fetches,
      // set image as input parameter
      feeds = Map(_imagePlaceholder -> image)
    )
    DetectionOutput(boxes, scores, classes, num)
  }

  // convert OpenCV tensor to TensorFlow tensor
  private def matToTensor(image: Mat): Tensor = {
    val imageRGB = new Mat
    cvtColor(image, imageRGB, COLOR_BGR2RGB) // convert channels from OpenCV GBR to RGB
    val imgBuffer = imageRGB.createBuffer[ByteBuffer]
    val shape = Shape(1, image.size.height, image.size.width, image.channels)
    Tensor.fromBuffer(UINT8, shape, imgBuffer.capacity, imgBuffer)
  }
}
