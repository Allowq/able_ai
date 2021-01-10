package ru.able.system.module

import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.name.Named
import ru.able.camera.camera.reader.BroadcastMaterializer
import ru.able.camera.utils.settings.Settings
import ru.able.system.SystemInitializer

import scala.concurrent.ExecutionContext

class SystemInitilaizerProvider @Inject()(settings: Settings,
                                          system: ActorSystem,
                                          broadCastMaterializer: BroadcastMaterializer,
                                          @Named("RouterFSM") pluginRegistry: ActorRef)
                                         (implicit @Named("StartUpEC") ec: ExecutionContext) extends Provider[ActorRef]
{
  override def get(): ActorRef =
    system.actorOf(
      SystemInitializer.props(broadCastMaterializer, pluginRegistry, settings: Settings)(ec),
      SystemInitializer.Name
    )
}
