package ru.able.communication

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import ru.able.camera.camera.CameraFrame
import ru.able.camera.utils.settings.Settings

object Notifier {

  def props(settings: Settings, communication: Communication)(implicit actorSystem: ActorSystem): Props = {
    Props(new Notifier(communication))
  }
}

class Notifier(communication: Communication) extends Actor with ActorLogging {

  private val pool = java.util.concurrent.Executors.newFixedThreadPool(5)

  override def receive: Receive = {
    case frame: CameraFrame =>
      pool.execute(
        () =>
          communication.sendBatch(Seq(frame)) match {
            case Left(msg)  => log.warning(msg)
            case Right(msg) => {
              log.info(msg)
            }
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
  }
}
