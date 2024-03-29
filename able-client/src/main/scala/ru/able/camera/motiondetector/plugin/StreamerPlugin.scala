package ru.able.camera.motiondetector.plugin

import akka.actor.ActorRef
import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import akka.stream.scaladsl.Sink
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgproc.putText
import ru.able.camera.framereader.model.CameraFrame
import ru.able.router.model.{AdvancedPluginStart, Plugin}

import scala.concurrent.duration.DurationInt
import scala.util.Try

class StreamerPlugin(notifier: ActorRef)(implicit mat: Materializer) extends Plugin with LazyLogging
{
  var pluginKillSwitch: Option[SharedKillSwitch] = None

  override def start(ps: AdvancedPluginStart): Unit =
    Try {
      pluginKillSwitch = Some(KillSwitches.shared("Streamer"))
      val (broadcast, killSwitch) = (ps.broadcast, ps.ks.sharedKillSwitch)

      broadcast.mat
        .via(killSwitch.flow)
        .via(pluginKillSwitch.get.flow)
        .async
        .map(printPluginId)
        .groupedWithin(5, 1000 millis)
        .runWith(Sink.foreach(sendNotificationBatch))
    } recover {
      case e: Exception => logger.error(e.getMessage, e)
    }

  private def sendNotificationBatch(f: Seq[CameraFrame]) = {
    f.foreach(frame => logger.debug(s"send ${frame.date}"))
    notifier ! f
  }

  private def sendNotification(f: CameraFrame) = notifier ! f

  private def printPluginId(cf: CameraFrame): CameraFrame = {
//    logger.debug(" new frame " + cf.date)

    val box_text_l = "Streamer ====================================="
    val imgMat     = cf.imgMat
    val point      = new opencv_core.Point(50, 20)
    val scalar     = new opencv_core.Scalar(0, 255, 0, 2.0)
    val font       = FONT_HERSHEY_PLAIN
    putText(imgMat, box_text_l, point, font, 1.0, scalar)

    CameraFrame(imgMat, cf.date)
  }

  override def stop(): Unit = pluginKillSwitch match {
    case Some(ks) => ks.shutdown()
    case None     => logger.error("shutdown")
  }
}
