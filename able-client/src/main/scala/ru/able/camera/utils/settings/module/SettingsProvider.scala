package ru.able.camera.utils.settings.module

import com.google.inject.Provider
import ru.able.camera.utils.settings.{PropertyFileSettingsLoader, Settings}

class SettingsProvider extends Provider[Settings] {
  override def get(): Settings = new PropertyFileSettingsLoader().load()
}
