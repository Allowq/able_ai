package ru.able.router

import akka.actor.{ActorRef, FSM, Props}
import akka.util.Timeout
import ru.able.camera.utils.settings.Settings
import ru.able.router.model.StatusMsgFSM._
import ru.able.router.model.{Active, Error, GoToIdle, Idle, Ready, RequestFSM, Start, StateFSM, Status, Stop, Waiting}

object SwitchFSM {

  val Name = classOf[SwitchFSM].getName

  def props(systemInitializer: ActorRef, settings: Settings) =
    Props(new SwitchFSM(systemInitializer, settings))
}

/**
  * Handling Active/Idle stages
  * shutdown killSwitch on Stop
  * delegate state changes to a router
  */
class SwitchFSM(systemInitializer: ActorRef, settings: Settings) extends FSM[StateFSM, RequestFSM]
{
  private val duration         = settings.getDuration("system.options.startUpTimeout")
  private implicit val timeout = Timeout(duration)

  startWith(Idle, Stop)

  when(Waiting) {
    case Event(Status(Right(Ok)), _) =>
      goto(Active)
    case Event(Status(Left(e)), _) =>
      log.error(e.getMessage, e)
      goto(Idle)
    case Event(GoToIdle, _) =>
      goto(Idle)
  }

  when(Idle) {
    case Event(Start(ks), _) =>
      log.debug("Start request")
      systemInitializer ! Start(ks)
      goto(Waiting) using Start(ks)
  }

  when(Active) {
    case Event(Stop, _) =>
      goto(Idle)
  }

  onTransition {
    case Waiting -> Idle =>
      stateData match {
        case Start(ks) =>
          ks.shutdown()
        case _ =>
          log.warning("received unhandled request {} in state Active", stateName)
      }
    case Active -> Idle =>
      stateData match {
        case Start(ks) =>
          systemInitializer ! Stop
          ks.shutdown()
        case _ =>
          log.warning("received unhandled request {} in state Active", stateName)
      }
  }

  whenUnhandled {
    case Event(Start(_), Start(_)) =>
      log.error(AlreadyStarted)
      sender() ! Error(AlreadyStarted)
      stay
    case Event(Stop, Stop) =>
      log.error(Finished)
      sender() ! Error(Finished)
      stay
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  initialize()
}
