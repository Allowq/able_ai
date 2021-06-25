package ru.able.controller

import ru.able.model.{DetectionOutput, DetectorModel, DetectorViaFileDescription}
import java.nio.ByteBuffer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{Graph, Materializer, SourceShape}
import com.typesafe.config.ConfigFactory
import org.bytedeco.javacpp.opencv_imgcodecs.imread
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_imgproc.{COLOR_BGR2RGB, cvtColor}
import org.bytedeco.javacv.Frame
import org.platanios.tensorflow.api.{Shape, UINT8}
import org.platanios.tensorflow.api.Tensor
import ru.able.utils.settings.PropertyBasedSettings

final class DetectorController private (_detectorModel: DetectorModel)
{
  private lazy val _config = ConfigFactory.defaultApplication().resolve().getConfig("sourceDescription")

  def this(folderPath: Option[String] = None) {
    this(
      new DetectorViaFileDescription(folderPath).defineDetector()
    )
  }

  def detectOnImage(image: Mat): DetectionOutput = { detect(matToTensor(image)) }

  def getImageByPath(pathToImage: String): Mat = { imread(pathToImage) }

  def getLabel(index: Int): String = _detectorModel.getLabelByIndex(index)

  def sourceCamera(cameraIdx: Option[String])(implicit system: ActorSystem): Source[Frame, NotUsed] = {
    val sourceGraph: Graph[SourceShape[Frame], NotUsed] =
      new CatchSource[PropertyBasedSettings](
        new PropertyBasedSettings(_config.getConfig("cameraConfig")),
        cameraIdx.getOrElse(_config.getString("cameraSource"))
      )
    Source.fromGraph(sourceGraph)
  }

  def sourceVideo(pathToVideo: Option[String])(implicit mat: Materializer): Source[Frame, NotUsed] = {
    val sourceGraph: Graph[SourceShape[Frame], NotUsed] =
      new CatchSource[PropertyBasedSettings](
        new PropertyBasedSettings(_config.getConfig("videoConfig")),
        pathToVideo.getOrElse(_config.getString("videoSource"))
      )(mat)
    Source.fromGraph(sourceGraph)
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



