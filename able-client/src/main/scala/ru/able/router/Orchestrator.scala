package ru.able.router

import akka.actor.ActorRef
import akka.stream.KillSwitches
import akka.util.Timeout
import com.google.inject.Inject
import com.google.inject.name.Named
import com.typesafe.scalalogging.LazyLogging
import ru.able.camera.utils.settings.Settings
import ru.able.router.model.Orchestrator.GlobalKillSwitch
import ru.able.router.PluginRegistryFSM.{Add, Remove}
import ru.able.router.model.{Plugin, Start, Stop}

class Orchestrator @Inject()(@Named("SwitchFSM") switch: ActorRef,
                             @Named("PluginRegistryFSM") pluginRegistry: ActorRef,
                             settings: Settings) extends LazyLogging
{
  private implicit val timeout = Timeout(settings.getDuration("system.options.startUpTimeout"))

  def addPlugin(plugin: Plugin): Unit = pluginRegistry ! Add(plugin)
  def removePlugin(plugin: Plugin): Unit = pluginRegistry ! Remove(plugin)

  def start(): Unit = switch ! Start(createKillSwitch)
  def stop(): Unit = switch ! Stop

  private def createKillSwitch() = GlobalKillSwitch(KillSwitches.shared("GlobalKillSwitch"))
}
