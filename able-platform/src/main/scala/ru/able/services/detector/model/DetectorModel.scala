package ru.able.services.detector.model

import org.platanios.tensorflow.api.{Graph, Session, Tensor}

case class DetectionOutput(boxes: Tensor, scores: Tensor, classes: Tensor, num: Tensor)

final case class DetectorModel(graph: Graph,
                               tfSession: Session,
                               labelMap: Map[Int, String])
{
  def getLabelByIndex(index: Int): String = labelMap.getOrElse(index, "unknown")
}