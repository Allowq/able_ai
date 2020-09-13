package ru.able.model

import org.platanios.tensorflow.api.{Graph, Session}

final case class DetectorModel(private val _graph: Graph,
                               private val _session: Session,
                               private val _labelMap: Map[Int, String])
{
  def graph: Graph = _graph
  def session: Session = _session
  def labelMap: Map[Int, String] = _labelMap
  def getLabelByIndex(index: Int): String = labelMap.getOrElse(index, "unknown")
}