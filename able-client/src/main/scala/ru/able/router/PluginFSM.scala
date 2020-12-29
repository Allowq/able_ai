package ru.able.router

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.FSM
import akka.actor.Props
import akka.routing.Router
import akka.routing.RoutingLogic
import akka.routing.SeveralRoutees
import akka.stream.KillSwitch
import akka.util.Timeout
import ru.able.camera.camera.reader.BroadCastRunnableGraph
import ru.able.camera.utils.settings.Settings
import ru.able.router.messages.Messages._
import ru.able.router.messages._

import scala.concurrent.ExecutionContext

object PluginFSM {

  final val Name = classOf[PluginFSM].getName

  def props(router: ActorRef, settings: Settings)(
      implicit ec: ExecutionContext,
      system: ActorSystem) =
    Props(new PluginFSM(router, settings)(ec, system))

}

/**
  * Route Start/Stop messages to Plugins
  *
  * @param router RouterFSM
  * @param settings
  * @param ec
  * @param system
  */
@deprecated
class PluginFSM(router: ActorRef, settings: Settings)(implicit val ec: ExecutionContext, val system: ActorSystem)
    extends FSM[State, Request] {

  private implicit val pluginTimeout = Timeout(settings.getDuration("system.options.pluginsTimeout", TimeUnit.SECONDS))
  startWith(Idle, Stop)

  when(Waiting) {
    case Event(GoToActive, _) =>
      goto(Active) using NoRequest

    case Event(GoToIdle, _) =>
      goto(Idle) using Stop

    case Event(Ready(Ok), WaitingForRoutees(requestor, remainingResponses)) =>
      if (remainingResponses == 0) self ! GoToActive
      stay() using WaitingForRoutees(requestor, remainingResponses - 1)

    case Event(Ready(Finished), WaitingForRoutees(requestor, remainingResponses)) =>
      if (remainingResponses == 0) self ! GoToIdle
      stay() using WaitingForRoutees(requestor, remainingResponses - 1)

    case Event(RouterTimeouted, WaitingForRoutees(requestor, _)) =>
      //      router.route(Stop, self)
      log.error(s"Router timeouted after ${settings
        .getDuration("system.options.pluginTimeout", TimeUnit.SECONDS)}")
      requestor ! Error(s"Router timeouted after ${settings
        .getDuration("system.options.pluginTimeout", TimeUnit.SECONDS)}")
      goto(Idle) using Stop
  }

  private def startRoutees(broadcast: BroadCastRunnableGraph, ks: KillSwitch) = {
    router ! PluginStart(ks, broadcast)
    scheduleRouterTimeoutCheck
  }

  private def scheduleRouterTimeoutCheck =
    system.scheduler.scheduleOnce(settings.getDuration("system.options.pluginTimeout", TimeUnit.SECONDS)) {
      self ! RouterTimeouted
    }

  onTransition {
    case Waiting -> Active =>
      stateData match {
        case WaitingForRoutees(requestor, _) => requestor ! Ready(Ok)
      }

    case Waiting -> Idle =>
      stateData match {
        case WaitingForRoutees(requestor, _) =>
          requestor ! Ready(Finished)
      }
  }

  when(Idle) {
    case Event(PluginStart(killSwitch, broadcast), _) =>
      log.debug("Start request")
      startRoutees(broadcast, killSwitch)
      goto(Waiting) using WaitingForRoutees(sender, 1)
  }

  when(Active) {
    case Event(Stop, _) =>
      router ! Stop
      goto(Waiting) using WaitingForRoutees(sender, 1)
  }

  whenUnhandled {
    case Event(PluginStart(_, _), NoRequest) =>
      log.error(AlreadyStarted)
      sender() ! Error(AlreadyStarted)
      stay
    case Event(Stop, Stop) =>
      log.error(Finished)
      sender() ! Error(Finished)
      stay
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}, sender: {}", e, stateName, s, sender)
      stay
  }

  initialize()
}
