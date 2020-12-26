package ru.able.utils.settings

import com.typesafe.config.Config
import scala.collection.JavaConverters._
import scala.util.Try

sealed trait Settings {
  def getOptions(path: String): Map[String, AnyRef]
  def getInt(path: String): Int
  def getDouble(path: String): Double
  def getString(path: String): String
}

trait VideoSettings extends Settings {
  def videoSourcePath(): String
}

trait CameraSettings extends Settings {
  def cameraSource(): String
  def cameraFormat(): String
}

class PropertyBasedSettings(config: Config) extends Settings {

  override def getOptions(path: String): Map[String, AnyRef] =
    options(path)
      .map(f => f.unwrapped.asScala.toMap)
      .getOrElse(Map.empty)

  override def getInt(path: String): Int = config.getInt(path)

  override def getDouble(path: String): Double = config.getDouble(path)

  override def getString(path: String): String = config.getString(path)

  private def options(path: String) =
    Try(Some(config.getObject(path))).recover { case _ => None }.get
}

class CameraBasedSettings(config: Config) extends PropertyBasedSettings(config) with CameraSettings {
  override def cameraSource(): String = config.getString("camera.macSource")
  override def cameraFormat(): String = config.getString("camera.format")
}

class VideoBasedSettings(config: Config) extends PropertyBasedSettings(config) with VideoSettings {
  override def videoSourcePath(): String = config.getString("video.defaultSource")
}