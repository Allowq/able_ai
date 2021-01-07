package ru.able.router

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.FSM
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import ru.able.camera.utils.settings.Settings
import ru.able.router.messages.Messages._
import ru.able.router.messages._

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success

object CameraFSM {

  val Name = classOf[CameraFSM].getName

  def props(cameraSource: ActorRef, router: ActorRef, settings: Settings)
           (implicit ec: ExecutionContext, system: ActorSystem) =
  {
    Props(new CameraFSM(cameraSource, router, settings))
  }
}

/**
  * Delegates Start/Stop messages to CameraSource then the Router
  *
  * @param cameraSource actor ref for handling camera source
  * @param router       actor ref for handling router
  * @param settings     config
  * @param ec           Execution context for handling actor ask
  * @param system       ActorSystem
  */
@deprecated
class CameraFSM(cameraSource: ActorRef, router: ActorRef, settings: Settings)(implicit val ec: ExecutionContext,
                                                                              val system: ActorSystem)
    extends FSM[State, Request] {

  private val sourceTimeout = Timeout(settings.getDuration("system.options.sourceTimeout", TimeUnit.SECONDS))
  private val routerTimeout = Timeout(settings.getDuration("system.options.routerTimeout", TimeUnit.SECONDS))

  startWith(Idle, Stop)

  when(Waiting) {
    case Event(GoToActive, _) =>
      goto(Active) using NoRequest

    case Event(GoToIdle, _) =>
      goto(Idle) using Stop

    case Event(Error(reason), WaitingForSource(requestor, _)) =>
      requestor ! Error(reason)
      goto(Idle) using Stop

    case Event(Error(reason), WaitingForRouter(requestor)) =>
      requestor ! Error(reason)
      goto(Idle) using Stop

    case Event(SourceInit(broadcast), WaitingForSource(sender, Start(ks))) =>
//      sendRequest(router, PluginStart(ks, broadcast))(routerTimeout)
      goto(Waiting) using WaitingForRouter(sender)

    case Event(Ready(Ok), WaitingForRouter(_)) =>
      goto(Active) using NoRequest

    case Event(Ready(Finished), WaitingForRouter(_)) =>
      goto(Idle) using Stop
  }

  onTransition {
    case Waiting -> Active =>
      stateData match {
        case WaitingForRouter(requestor) => requestor ! Ready(Ok)
      }

    case Waiting -> Idle =>
      stateData match {
        case WaitingForRouter(requestor) =>
          requestor ! Ready(Finished)
        case WaitingForSource(_, _) =>
          log.error("CameraFSM timed out while waiting for source")
      }
  }

  when(Idle) {
    case Event(Start(ks), _) =>
      sendRequest(cameraSource, Start(ks))(sourceTimeout)
      goto(Waiting) using WaitingForSource(sender, Start(ks))
  }

  private def sendRequest(actor: ActorRef, request: Request)(implicit timeout: akka.util.Timeout) = {
    log.debug("{} request sent to {}, timeout: {}", request, actor, timeout)
    ask(actor, request)
      .mapTo[Response]
      .onComplete {
        case Success(unknowMessage) =>
          unknowMessage match {
            case request: Response =>
              log.debug("{} responded with {}", actor, request)
              self ! request
            case _ => {
              log.warning("{} responded with unknown message {}", actor.getClass.getName, unknowMessage)
              self ! Error(unknowMessage.toString)
            }
          }
        case Failure(e) =>
          log.error("Error occurred while waiting for response: {}", e)
          self ! Error(e.getMessage)
      }
  }

  when(Active) {
    case Event(Stop, _) =>
      sendRequest(router, Stop)(routerTimeout)
      goto(Waiting) using WaitingForRouter(sender)
  }

  whenUnhandled {
    case Event(Start(_), NoRequest) =>
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
