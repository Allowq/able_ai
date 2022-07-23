package ru.able.communication.viasocket

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import ru.able.camera.framereader.model.CameraFrame
import ru.able.camera.utils.settings.Settings

object Notifier {
  val ProviderName = "Notifier"

  def props(settings: Settings, communication: Communication)(implicit actorSystem: ActorSystem): Props = {
    Props(new Notifier(communication))
  }
}

class Notifier(communication: Communication) extends Actor with ActorLogging {

  private val pool = java.util.concurrent.Executors.newFixedThreadPool(2)

  override def receive: Receive = {
    case frame: CameraFrame =>
      pool.execute(
        () =>
          communication.sendBatch(Seq(frame)) match {
            case Left(msg)  => log.warning(msg)
            case Right(msg) => log.info(msg)
        }
      )
    case frames: Seq[CameraFrame] =>
      pool.execute(
        () =>
          communication.sendBatch(frames) match {
            case Left(msg)  => log.warning(msg)
            case Right(msg) => log.info(msg)
          }
      )
    case msg => log.warning(s"NotifierActor (via socket) cannot parse incoming request: $msg!")
  }
}
