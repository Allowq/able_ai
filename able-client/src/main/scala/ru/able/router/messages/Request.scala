package ru.able.router.messages

import akka.actor.ActorRef
import akka.stream.KillSwitch
import ru.able.camera.camera.reader.BroadCastRunnableGraph
import ru.able.camera.camera.reader.KillSwitches.GlobalKillSwitch

sealed trait Request

case object NoRequest extends Request

case object Stop extends Request

case object Started extends Request

case class Start(gks: GlobalKillSwitch) extends Request

case class PluginStart(ks: KillSwitch, broadcast: BroadCastRunnableGraph) extends Request
case class AdvancedPluginStart(ks: GlobalKillSwitch, broadcast: BroadCastRunnableGraph) extends Request

case class WaitingForRoutees(requestor: ActorRef, numberOfResponses: Int) extends Request

case class WaitingForRouter(requestor: ActorRef) extends Request

case class WaitingForSource(requestor: ActorRef, request: Request) extends Request

private[router] case object GoToActive extends Request

private[router] case object GoToIdle extends Request

private[router] case object RouterTimeouted extends Request
