package ru.able.camera.utils.settings.module

import com.google.inject.AbstractModule
import ru.able.camera.utils.settings.Settings

class SettingsInjector extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[Settings]).toProvider(classOf[SettingsProvider])
  }
}
