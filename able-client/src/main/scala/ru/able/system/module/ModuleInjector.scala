package ru.able.system.module

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject._
import ru.able.camera.framereader.graph.source.module.CameraReaderInjector
import ru.able.camera.motiondetector.bgsubtractor.module.BackgroundSubstractorInjector
import ru.able.camera.utils.settings.module.SettingsInjector
import ru.able.communication.viasocket.module.ConnViaSocketInjector
import ru.able.communication.viatcp.module.ConnViaTCPInjector
import ru.able.router.module.RouterInjector

class ModuleInjector(system: ActorSystem, materializer: Materializer) {
  val injector = Guice.createInjector(
    new SystemInitializerInjector(system, materializer),
    new ConnViaSocketInjector(),
    new SettingsInjector(),
    new BackgroundSubstractorInjector(),
    new CameraReaderInjector(),
    new RouterInjector(),
    new ConnViaTCPInjector()
  )
}
