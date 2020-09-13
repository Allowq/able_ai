package ru.able.controller

import ru.able.model.{DetectorModel, DetectorViaFileDescription, DetectionOutput}
import org.platanios.tensorflow.api.Tensor

final class DetectorController private (private val _detectorModel: DetectorModel)
{
  def this(folderPath: Option[String] = None) {
    this(
      new DetectorViaFileDescription(folderPath).defineDetector()
    )
  }

  def getLabel(index: Int): String = _detectorModel.getLabelByIndex(index)

  // run the object detection model on an image
  def detect(image: Tensor): DetectionOutput = {
    // retrieve the output placeholders
    val imagePlaceholder = _detectorModel.graph.getOutputByName("image_tensor:0")
    val detectionBoxes = _detectorModel.graph.getOutputByName("detection_boxes:0")
    val detectionScores = _detectorModel.graph.getOutputByName("detection_scores:0")
    val detectionClasses = _detectorModel.graph.getOutputByName("detection_classes:0")
    val numDetections = _detectorModel.graph.getOutputByName("num_detections:0")

    // set image as input parameter
    val feeds = Map(imagePlaceholder -> image)

    // Run the detection model
    val Seq(boxes, scores, classes, num) =
      _detectorModel.session.run(fetches = Seq(detectionBoxes, detectionScores, detectionClasses, numDetections), feeds = feeds)
    DetectionOutput(boxes, scores, classes, num)
  }
}

