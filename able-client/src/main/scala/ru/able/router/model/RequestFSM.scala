package ru.able.router.model

import ru.able.camera.framereader.graph.broadcast.BroadcastRunnableGraph
import ru.able.router.model.Orchestrator.GlobalKillSwitch

sealed trait RequestFSM
case object Stop extends RequestFSM
case class Start(gks: GlobalKillSwitch) extends RequestFSM
case class AdvancedPluginStart(ks: GlobalKillSwitch, broadcast: BroadcastRunnableGraph) extends RequestFSM

private[router] case object GoToIdle extends RequestFSM
