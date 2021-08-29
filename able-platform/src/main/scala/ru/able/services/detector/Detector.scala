package ru.able.services.detector

import java.nio.ByteBuffer

import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_imgproc.{COLOR_BGR2RGB, cvtColor}
import org.platanios.tensorflow.api.{Shape, Tensor, UINT8}
import ru.able.services.detector.model.{DetectionOutput, DetectorDescriber}

final class Detector(folderPath: Option[String] = None)
{
  private val _detectorModel = new DetectorDescriber().describe()
  // retrieve the output placeholders
  private val _imagePlaceholder = _detectorModel.graph.getOutputByName("image_tensor:0")
  private val _fetches = Seq(
    _detectorModel.graph.getOutputByName("detection_boxes:0"),
    _detectorModel.graph.getOutputByName("detection_scores:0"),
    _detectorModel.graph.getOutputByName("detection_classes:0"),
    _detectorModel.graph.getOutputByName("num_detections:0")
  )

  def detect(image: Mat): DetectionOutput = detect(matToTensor(image))

  // run the object detection model on an image
  def detect(image: Tensor): DetectionOutput = {
    // Run the detection model
    val Seq(boxes, scores, classes, num) = _detectorModel.tfSession.run(
      fetches = _fetches,
      // set image as input parameter
      feeds = Map(_imagePlaceholder -> image)
    )

    DetectionOutput(boxes, scores, classes, num)
  }

  def dictionary: Map[Int, String] = _detectorModel.labelMap

  def index(i: Int): String = _detectorModel.labelMap.getOrElse(i, "unknown")

  // convert OpenCV tensor to TensorFlow tensor
  private def matToTensor(image: Mat): Tensor = {
    val imageRGB = new Mat
    cvtColor(image, imageRGB, COLOR_BGR2RGB) // convert channels from OpenCV GBR to RGB
    val imgBuffer = imageRGB.createBuffer[ByteBuffer]
    val shape = Shape(1, image.size.height, image.size.width, image.channels)
    Tensor.fromBuffer(UINT8, shape, imgBuffer.capacity, imgBuffer)
  }
}
