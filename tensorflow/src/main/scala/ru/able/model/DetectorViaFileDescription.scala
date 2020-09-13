package ru.able.model

import java.io.{BufferedInputStream, File, FileInputStream}
import scala.io.Source
import scala.util.matching.Regex

import object_detection.protos.string_int_label_map.{StringIntLabelMap, StringIntLabelMapItem}

import org.platanios.tensorflow.api.{Graph, Session}
import org.tensorflow.framework.GraphDef

final case object DetectorViaFileDescription {
  def getDefaultDescriptionFolderPath: String = System.getProperty("user.dir") + "/data/models/default"
  def getDefaultDescriptionFolder: File = new File(getDefaultDescriptionFolderPath)
}

class DetectorViaFileDescription {
  private var _inferenceGraphPath: String =
    DetectorViaFileDescription.getDefaultDescriptionFolderPath + "/ssd_inception_v2_coco_2018_01_28/frozen_inference_graph.pb"
  private var _labelMapPath: String =
    DetectorViaFileDescription.getDefaultDescriptionFolderPath + "/mscoco_label_map.pbtxt"

  def this(folderPath: Option[String] = None) = {
    this()
    folderPath.foreach(describeModel(_))
  }

  def defineDetector(): DetectorModel = {
    // load a pretrained detection model as TensorFlow graph
    val graphDef = GraphDef.parseFrom(
      new BufferedInputStream(
        new FileInputStream(
          new File(_inferenceGraphPath)
        )
      )
    )

    val graph = Graph.fromGraphDef(graphDef)

    // load the protobuf label map containing the class number to string label mapping (from COCO)
    val labelMap: Map[Int, String] = {
      val pbText = Source.fromFile(_labelMapPath).mkString
      val stringIntLabelMap = StringIntLabelMap.fromAscii(pbText)
      stringIntLabelMap.item.collect {
        case StringIntLabelMapItem(_, Some(id), Some(displayName)) => id -> displayName
      }.toMap
    }

    DetectorModel(
      graph,
      Session(graph),
      labelMap
    )
  }

  def inferenceGraphPath: String = _inferenceGraphPath
  def labelMapPath: String = _labelMapPath

  private def describeModel(defineFolderPath: String): Unit = {
    def go(t: File, r: Regex): Array[File] = {
      val these = t.listFiles()
      val aim = these.filter(f => r.findFirstIn(f.getName).isDefined)
      aim ++ these.filter(_.isDirectory).flatMap(go(_, r))
    }

    val frozenGraphProto = go(new File(defineFolderPath), """.*\.pb$""".r) match {
      case f: Array[File] if f.size > 0 => Some(f.head.getAbsolutePath)
      case _ => None
    }

    val labelMapProto = go(new File(defineFolderPath), """.*\.pbtxt$""".r) match {
      case f: Array[File] if f.size > 0 => Some(f.head.getAbsolutePath)
      case _ => None
    }

    (frozenGraphProto, labelMapProto) match {
      case (Some(a), Some(b)) => {
        _inferenceGraphPath = a
        _labelMapPath = b
      }
      case _ => None
    }
  }
}

