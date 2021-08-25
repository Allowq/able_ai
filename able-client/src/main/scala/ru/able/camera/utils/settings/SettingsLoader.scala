package ru.able.camera.utils.settings

import com.typesafe.config.{Config, ConfigFactory}
import SettingsLoader._

object SettingsLoader {
  val WindowsConf   = "windows"
  val LinuxARMConf  = "linux-arm"
  val MacOSConf     = "macos"
  val DefaultConf   = "default"
}

trait SettingsLoader {
  def load(): Settings
}

class PropertyFileSettingsLoader extends SettingsLoader {

  private lazy val os = System.getProperty("os.name")

  override def load(): Settings = {
    val conf: Config = ConfigFactory
      .load(determineConfigFile)
      .withFallback(ConfigFactory.load(DefaultConf))
      .resolve

    new PropertyBasedSettings(conf)
  }

  private def determineConfigFile: String = {
    os.toLowerCase match {
      case windowsOS if windowsOS.startsWith("windows") => WindowsConf
      case macOS if macOS.startsWith("mac") => MacOSConf
      case _ => LinuxARMConf
    }
  }
}
