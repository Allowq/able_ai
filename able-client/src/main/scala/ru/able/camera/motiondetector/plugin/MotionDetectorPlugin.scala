package ru.able.camera.motiondetector.plugin

import akka.actor.ActorRef
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgproc._

import ru.able.camera.framereader.model.{CameraFrame, MotionDetectFrame}
import ru.able.camera.motiondetector.bgsubtractor.BackgroundSubstractor
import ru.able.camera.motiondetector.plugin.stage.BackgroundSubstractorStage
import ru.able.router.model.{AdvancedPluginStart, Plugin}

import scala.util.Try

class MotionDetectorPlugin(backgroundSubstractor: BackgroundSubstractor, notifier: ActorRef)
                          (implicit mat: Materializer) extends Plugin with LazyLogging
{
  val structuringElementSize = new Size(4, 4)
  var pluginKillSwitch: Option[SharedKillSwitch] = None

  override def start(ps: AdvancedPluginStart): Unit = {
    Try{
      pluginKillSwitch = Some(KillSwitches.shared("BackgroundSubstractor"))
      val (broadcast, killSwitch) = (ps.broadcast, ps.ks.sharedKillSwitch)

      broadcast.mat
        .via(killSwitch.flow)
        .via(pluginKillSwitch.get.flow)
        .async
        .map(printPluginId)
        .via(new BackgroundSubstractorStage(backgroundSubstractor))
        .via(Flow[MotionDetectFrame].filter(reachedThreshold))
        .via(Flow[MotionDetectFrame].map(_.originalFrame))
        .runWith(Sink.foreach(sendNotification))
    } recover {
      case e: Exception => logger.error(e.getMessage, e)
    }
  }

  override def stop(): Unit = pluginKillSwitch match {
    case Some(ks) => ks.shutdown()
    case None     => logger.error("shutdown")
  }

  private def printPluginId(cf: CameraFrame): CameraFrame = {
    val box_text_l = "Motion Detector ==============================="
    val imgMat     = cf.imgMat
    val point      = new opencv_core.Point(50, 60)
    val scalar     = new opencv_core.Scalar(0, 255, 0, 2.0)
    val font       = FONT_HERSHEY_PLAIN
    putText(imgMat, box_text_l, point, font, 1.0, scalar)

    CameraFrame(imgMat, cf.date)
  }

  private def reachedThreshold(f: MotionDetectFrame): Boolean = cvCountNonZero(f.maskedImg) > 5000

  private def sendNotification(f: CameraFrame): Unit = notifier ! f
}
