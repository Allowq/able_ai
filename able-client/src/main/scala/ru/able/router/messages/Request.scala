package ru.able.router.messages

import akka.stream.KillSwitch
import ru.able.camera.camera.reader.BroadCastRunnableGraph
import ru.able.common.Switches.GlobalKillSwitch

sealed trait Request
case object Stop extends Request

case class Start(gks: GlobalKillSwitch) extends Request
case class PluginStart(ks: KillSwitch, broadcast: BroadCastRunnableGraph) extends Request
case class AdvancedPluginStart(ks: GlobalKillSwitch, broadcast: BroadCastRunnableGraph) extends Request

private[router] case object GoToIdle extends Request
