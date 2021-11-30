package ru.able.system

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern.pipe
import akka.util.Timeout
import com.google.inject.Inject
import ru.able.camera.framereader.BroadcastMaterializer
import ru.able.camera.framereader.graph.broadcast.BroadcastRunnableGraph

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import scala.util.Success
import ru.able.camera.utils.settings.Settings
import ru.able.router.model.StatusMsgFSM.Ok
import ru.able.router.model.{AdvancedPluginStart, RequestFSM, Start, Status, Stop}

object SystemInitializer {
  val Name = "SystemInitializer"

  def props(broadCastMaterializer: BroadcastMaterializer, pluginRegistry: ActorRef, settings: Settings)
           (implicit ec: ExecutionContext)
  : Props =
  {
    Props(new SystemInitializer(broadCastMaterializer, pluginRegistry, settings))
  }
}

class SystemInitializer @Inject()(broadCastMaterializer: BroadcastMaterializer,
                                  pluginRegistry: ActorRef,
                                  settings: Settings)
                                 (implicit val ec: ExecutionContext) extends Actor
{
  private implicit val timeout = Timeout(settings.startupTimeoutDuration("system.options.startUpTimeout"))

  override def receive: Receive = {
    case Start(gks) =>
      val senderActor = sender()
      broadCastMaterializer.create(gks).onComplete {
        case Success(bs: BroadcastRunnableGraph) =>
          pluginRegistry ! AdvancedPluginStart(gks, bs)
          Future { Status(Right(Ok)) }.pipeTo(senderActor)
        case Failure(t) =>
          Future { Status(Left(t)) }.pipeTo(senderActor)
      }
    case Stop =>
      pluginRegistry ! Stop
  }
}
