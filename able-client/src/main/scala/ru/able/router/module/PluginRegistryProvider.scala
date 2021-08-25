package ru.able.router.module

import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.google.inject.Inject
import com.google.inject.Provider
import ru.able.camera.utils.settings.Settings
import ru.able.router.PluginRegistryFSM

class PluginRegistryProvider @Inject()(system: ActorSystem, settings: Settings) extends Provider[ActorRef]
{
  override def get(): ActorRef = system.actorOf(PluginRegistryFSM.props(settings), PluginRegistryFSM.Name)
}
