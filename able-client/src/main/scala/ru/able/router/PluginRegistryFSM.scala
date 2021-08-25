package ru.able.router

import akka.actor.{ActorSystem, FSM, Props}

import scala.util.{Failure, Success, Try}
import ru.able.camera.utils.settings.Settings
import ru.able.router.PluginRegistryFSM.{Add, Remove}
import ru.able.router.model.StatusMsgFSM.Finished
import ru.able.router.model.{Active, AdvancedPluginStart, Error, Idle, Plugin, Ready, StateFSM, Stop}

object PluginRegistryFSM {

  val Name = classOf[PluginRegistryFSM].getName

  case class Add(plugin: Plugin)
  case class Remove(plugin: Plugin)

  def props(settings: Settings) = Props(new PluginRegistryFSM(settings))
}

class PluginRegistryFSM(settings: Settings) extends FSM[StateFSM, PluginRouter] {

  startWith(Idle, PluginRouter.empty)

  when(Idle) {
    case Event(Add(plugin), router) =>
      log.debug("Add plugin: {}", plugin)
      stay using router.addPlugin(plugin)

    case Event(Remove(plugin), router) =>
      log.debug("Remove plugin: {}", plugin)
      stay using router.removePlugin(plugin)

    case Event(AdvancedPluginStart(killSwitch, broadcast), router) =>
      Try {
        val started = router.start(AdvancedPluginStart(killSwitch, broadcast))
        goto(Active) using started
      } recover {
        case e: Exception => {
          sender() ! Error(e.getMessage)
          goto(Idle) using router
        }
      } get
  }

  when(Active) {
    case Event(Stop, router) =>
      log.debug("Stop plugins")
      Try[State] {
        val stopped = router.stop()
        goto(Idle) using stopped
      } match {
        case Success(state) => state
        case Failure(e) =>
          sender() ! Error(e.getMessage)
          goto(Idle) using PluginRouter.empty
      }

    case Event(Add(plugin), router) =>
      log.debug("Add plugin: {}", plugin)
      Try(plugin.start(createPluginStart(router))) recover {
        case e: Exception => {
          sender() ! Error(e.getMessage)
          self ! Remove(plugin)
        }
      }
      stay using router.addPlugin(plugin)

    case Event(Remove(plugin), router) =>
      log.debug("Remove plugin: {}", plugin)
      Try(plugin.stop()) recover { case e: Exception => sender() ! Error(e.getMessage) }
      stay using router.removePlugin(plugin)
  }

  whenUnhandled {
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}, sender: {}", e, stateName, s, sender)
      stay
  }

  private def createPluginStart(router: PluginRouter) = AdvancedPluginStart(router.ks.get, router.bs.get)

  initialize()
}
