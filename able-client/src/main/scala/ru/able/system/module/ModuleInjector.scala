package ru.able.system.module

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject._
import ru.able.camera.camera.module.CameraInjector
import ru.able.camera.motiondetector.bgsubtractor.module.BackgroundSubstractorInjector
import ru.able.camera.utils.settings.module.SettingsInjector
import ru.able.router.module.RouterInjector

class ModuleInjector(system: ActorSystem, materializer: Materializer) {
  val injector = Guice.createInjector(
    new SystemInjector(system, materializer),
    new SettingsInjector(),
    new BackgroundSubstractorInjector(),
    new CameraInjector(),
    new RouterInjector()
  )
}
