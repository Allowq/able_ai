package ru.able.utils.settings

import com.typesafe.config.Config

sealed trait Settings {
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
  override def getInt(path: String): Int = config.getInt(path)
  override def getDouble(path: String): Double = config.getDouble(path)
  override def getString(path: String): String = config.getString(path)
}