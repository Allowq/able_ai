package ru.able.utils.settings

import com.typesafe.config.{Config, ConfigFactory}
import SettingsLoader._

object SettingsLoader {
  val macosConf = "application"
  val linuxARMConf = "linux-arm"
  val windowsConf = "windows"
  val defaultConf = "application"
}

trait SettingsLoader {
  def load(): Settings
}


class PropertyFileSettingsLoader extends SettingsLoader {

  private lazy val os = System.getProperty("os.name")

  override def load(): Settings = {
    val conf: Config = ConfigFactory.load(determineConfigFile)
      .withFallback(ConfigFactory.load(defaultConf))
      .resolve
    new PropertyBasedSettings(conf)
  }

  private def determineConfigFile: String =
    os.toLowerCase match {
      case w if w.startsWith("windows") => windowsConf
      case l if l.startsWith("linux") => linuxARMConf
      case _ => macosConf
    }
}