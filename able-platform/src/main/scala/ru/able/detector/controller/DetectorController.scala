package ru.able.detector.controller

import java.nio.ByteBuffer

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.ConfigFactory
import org.bytedeco.javacpp.opencv_imgcodecs.imread
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_imgproc.{COLOR_BGR2RGB, cvtColor}
import org.platanios.tensorflow.api.Tensor
import org.platanios.tensorflow.api._
import ru.able.detector.model.{DetectionOutput, DetectorModel, DetectorViaFileDescription}

object DetectorController {
  def props = Props(new DetectorController())
}

class DetectorController private (private val _detectorModel: DetectorModel) extends Actor with ActorLogging
{
  private lazy val _config = ConfigFactory.defaultApplication().resolve().getConfig("sourceDescription")

  def this(folderPath: Option[String] = None) {
    this(
      new DetectorViaFileDescription(folderPath).defineDetector()
    )
  }

  override def receive: Receive = {
    case image: Mat =>
      sender ! detect(matToTensor(image))
    case index: Int =>
      sender ! _detectorModel.labelMap.getOrElse(index, "unknown")
    case "get_dictionary" =>
      sender ! _detectorModel.labelMap
  }

  // run the object detection model on an image
  private def detect(image: Tensor): DetectionOutput = {
    // retrieve the output placeholders
    val imagePlaceholder = _detectorModel.graph.getOutputByName("image_tensor:0")
    val detectionBoxes = _detectorModel.graph.getOutputByName("detection_boxes:0")
    val detectionScores = _detectorModel.graph.getOutputByName("detection_scores:0")
    val detectionClasses = _detectorModel.graph.getOutputByName("detection_classes:0")
    val numDetections = _detectorModel.graph.getOutputByName("num_detections:0")

    // set image as input parameter
    val feeds = Map(imagePlaceholder -> image)

    // Run the detection model
    val Seq(boxes, scores, classes, num) = _detectorModel.session.run(
      fetches = Seq(detectionBoxes, detectionScores, detectionClasses, numDetections),
      feeds = feeds
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
