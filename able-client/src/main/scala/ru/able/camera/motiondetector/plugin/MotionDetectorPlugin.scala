package ru.able.camera.motiondetector.plugin

import akka.actor.ActorRef
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgproc._
import org.bytedeco.javacv._
import ru.able.camera.camera.CameraFrame
import ru.able.camera.camera.MotionDetectFrame
import ru.able.camera.motiondetector.bgsubtractor.BackgroundSubstractor
import ru.able.camera.motiondetector.stage.BackgroundSubstractorStage
import ru.able.plugin.Plugin
import ru.able.router.messages.AdvancedPluginStart
import ru.able.camera.utils.MediaConversion

import scala.util.Try

class MotionDetectorPlugin(canvas: CanvasFrame,
                           backgroundSubstractor: BackgroundSubstractor,
                           name: String = "",
                           notifier: ActorRef)(implicit mat: Materializer) extends Plugin with LazyLogging
{
  val structuringElementSize                     = new Size(4, 4)
  var pluginKillSwitch: Option[SharedKillSwitch] = None

  /**
    * @see https://docs.opencv.org/2.4.13.4/doc/tutorials/imgproc/erosion_dilatation/erosion_dilatation.html
    */
  private def erosionAndDilation(backgroundSubstractedFrame: MotionDetectFrame) = {
    val structuringElement = getStructuringElement(MORPH_RECT, structuringElementSize)
    val frameAsMat         = backgroundSubstractedFrame.originalFrame.imgMat
    morphologyEx(frameAsMat, frameAsMat, MORPH_OPEN, structuringElement)
//    frameAsMat.release()
    backgroundSubstractedFrame
  }

  override def start(ps: AdvancedPluginStart): Unit =
    Try({
      pluginKillSwitch = Some(KillSwitches.shared("BackgroundSubstractor"))
      val (broadcast, killSwitch) = (ps.broadcast, ps.ks.sharedKillSwitch)

      broadcast.mat
        .via(killSwitch.flow)
        .via(pluginKillSwitch.get.flow)
        .via(new BackgroundSubstractorStage(backgroundSubstractor))
        .async.via(Flow[MotionDetectFrame].map(erosionAndDilation))
        .async.via(Flow[MotionDetectFrame].filter(reachedThreshold))
        .async.via(Flow[MotionDetectFrame].map(_.originalFrame))
        .async.runWith(Sink.foreach(sendNotification))
      //        .runWith(new ShowImageStage(canvas, iplImageConverter, name))

    }) recover {
      case e: Exception => logger.error(e.getMessage, e)
    }

  private def sendNotification(f: CameraFrame) = notifier ! f

  private def reachedThreshold(f: MotionDetectFrame): Boolean =
    cvCountNonZero(f.maskedImg) > 5000

  override def stop(): Unit = pluginKillSwitch match {
    case Some(ks) => ks.shutdown()
    case None     => logger.error("shutdown")
  }
}
