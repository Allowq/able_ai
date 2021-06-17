package ru.able.services.detector.model

import org.platanios.tensorflow.api.{Graph, Session}

final case class DetectorModel(graph: Graph,
                               tfSession: Session,
                               labelMap: Map[Int, String])
{
  def getLabelByIndex(index: Int): String = labelMap.getOrElse(index, "unknown")
}