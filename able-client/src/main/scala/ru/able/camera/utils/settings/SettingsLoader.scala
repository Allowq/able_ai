package ru.able.camera.utils.settings

import com.typesafe.config.{Config, ConfigFactory}
import SettingsLoader._

object SettingsLoader {
  val WindowsConf = "windows"
  val LinuxARMConf = "linux-arm"
  val DefaultConf = "default"
}

trait SettingsLoader {
  def load(): Settings
}

class PropertyFileSettingsLoader extends SettingsLoader {

  private lazy val os = System.getProperty("os.name")

  /**
    * Loads settings from 'default.conf'
    * and additional settings from 'windows.conf' or 'linux-arm.conf'
    * depending on the os.
    * @return
    */
  override def load(): Settings = {
    val conf: Config = ConfigFactory.load(determineConfigFile)
      .withFallback(ConfigFactory.load(DefaultConf))
      .resolve
    new PropertyBasedSettings(conf)
  }

  private def determineConfigFile =
    if (os.toLowerCase.startsWith("windows")) WindowsConf
    else LinuxARMConf
}
