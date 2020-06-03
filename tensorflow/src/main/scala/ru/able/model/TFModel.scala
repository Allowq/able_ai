package ru.able.model

import java.io.File
import scala.util.matching.Regex

case class TFModel(graphProtoPath: String,
                   frozenGraphProtoPath: String)

case object TFModel {
  def describeModel(modelPath: String): Option[TFModel] = {
    def go(t: File, r: Regex): Array[File] = {
      val these = t.listFiles()
      val aim = these.filter(f => r.findFirstIn(f.getName).isDefined)
      aim ++ these.filter(_.isDirectory).flatMap(go(_, r))
    }

    val frozenGraphProto = go(new File(modelPath), """.*\.pb$""".r) match {
      case f: Array[File] if f.size > 0 => Some(f.head.getAbsolutePath)
      case _ => None
    }

    val graphProto = go(new File(modelPath), """.*\.pbtxt$""".r) match {
      case f: Array[File] if f.size > 0 => Some(f.head.getAbsolutePath)
      case _ => None
    }

    (graphProto, frozenGraphProto) match {
      case (Some(a), Some(b)) => Some(TFModel(a, b))
      case _ => None
    }
  }
}
