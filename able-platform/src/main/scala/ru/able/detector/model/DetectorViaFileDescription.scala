package ru.able.detector.model

import java.io.{BufferedInputStream, File, FileInputStream}
import java.net.URL

import scala.util.matching.Regex
import scala.io.Source
import sys.process._
import com.typesafe.config.ConfigFactory
import org.tensorflow.framework.GraphDef
import org.platanios.tensorflow.api.{Graph, Session}

import object_detection_structures.protos.string_int_label_map.{StringIntLabelMap, StringIntLabelMapItem}

final case object DetectorViaFileDescription {
  private lazy val _config = ConfigFactory.defaultApplication().resolve().getConfig("modelDescription")

  def getBaseDir: String = _config.getString("baseDir")
  def getModelURL: String = _config.getString("modelUrl")
  def getLabelMapURL: String = _config.getString("labelMapUrl")
  def getArchiveFileName: String = s"${_config.getString("modelName")}.tar.gz"
  def getModelName: String = _config.getString("modelName")

  def getDefaultModelPath: String = _config.getString("defaultModelPath")
  def getDefaultInferenceGraphPath: String = _config.getString("inferenceGraphPath")
  def getDefaultLabelMapPath: String = _config.getString("labelMapPath")
}

class DetectorViaFileDescription {
  private var _isSpecifiedModel = false
  private var _inferenceGraphPath: String = _
  private var _labelMapPath: String = _

  def this(folderPath: Option[String] = None) = {
    this()
    folderPath.foreach(describeModel(_))
    if (!isSpecifiedModel && downloadDefaultModel)
      loadDefaultValues
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

  def isSpecifiedModel: Boolean = _isSpecifiedModel
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
        _isSpecifiedModel = true
      }
      case _ => None
    }
  }

  private def downloadDefaultModel: Boolean = {
    val defaultModelPath: String = DetectorViaFileDescription.getDefaultModelPath

    if(!new File(defaultModelPath).exists()) {
      println(s"Couldn\'t find object detection model in \'${defaultModelPath}\'")
      if(!new File(defaultModelPath + ".tar.gz").exists()) {
        println(s"Please waiting for download model from \'${DetectorViaFileDescription.getModelURL}\'")
        new URL(DetectorViaFileDescription.getModelURL) #> new File(defaultModelPath + ".tar.gz") !!;
        println(s"Downloading has finished: \'${defaultModelPath}\'")
      }

      val cmd = s"tar -xzf ${defaultModelPath}.tar.gz -C " +
        s"${DetectorViaFileDescription.getBaseDir}"
      cmd.!!
    }

    val defaultLabelMapPath: String = DetectorViaFileDescription.getDefaultLabelMapPath
    if(!new File(defaultLabelMapPath).exists()) {
      println(s"Couldn\'t find labels for model in \'${defaultLabelMapPath}\'")
      println(s"Please waiting for download label map from \'${DetectorViaFileDescription.getLabelMapURL}\'")
      new URL(DetectorViaFileDescription.getLabelMapURL) #> new File(defaultLabelMapPath) !!;
      println(s"Downloading has finished: \'${defaultLabelMapPath}\'")
    }

    new File(defaultModelPath).exists()
  }

  private def loadDefaultValues: Unit = {
    _inferenceGraphPath = DetectorViaFileDescription.getDefaultInferenceGraphPath
    _labelMapPath = DetectorViaFileDescription.getDefaultLabelMapPath
  }
}
