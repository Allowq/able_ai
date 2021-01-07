package ru.able.system

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern.pipe
import akka.util.Timeout
import com.google.inject.Inject

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import scala.util.Success
import ru.able.camera.camera.reader.BroadCastRunnableGraph
import ru.able.camera.camera.reader.BroadcastMaterializer
import ru.able.camera.utils.settings.Settings
import ru.able.router.messages.Messages.Ok
import ru.able.router.messages._

object SystemInitializer {
  val Name = "SystemInitializer"

  def props(broadCastMaterializer: BroadcastMaterializer, pluginRegistry: ActorRef, settings: Settings)
           (implicit ec: ExecutionContext): Props =
  {
    Props(new SystemInitializer(broadCastMaterializer, pluginRegistry, settings))
  }
}

class SystemInitializer @Inject()(broadCastMaterializer: BroadcastMaterializer,
                                  pluginRegistry: ActorRef,
                                  settings: Settings)
                                 (implicit val ec: ExecutionContext) extends Actor
{
  private implicit val timeout = Timeout(settings.getDuration("system.options.startUpTimeout"))

  override def receive: Receive = {
    case Start(gks) =>
      val senderActor = sender()
      broadCastMaterializer.create(gks).future.onComplete {
        case Success(bs: BroadCastRunnableGraph) =>
          pluginRegistry ! AdvancedPluginStart(gks, bs)
          Future { Status(Right(Ok)) }.pipeTo(senderActor)
        case Failure(t) =>
          Future { Status(Left(t)) }.pipeTo(senderActor)
      }
  }
}
