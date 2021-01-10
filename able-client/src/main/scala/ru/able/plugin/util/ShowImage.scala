package ru.able.plugin.util

import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_core.{FONT_HERSHEY_PLAIN, Mat}
import org.bytedeco.javacpp.opencv_imgproc.putText
import org.bytedeco.javacv.CanvasFrame
import ru.able.camera.camera.CameraFrame
import ru.able.camera.camera.stage.ShowImageStage
import ru.able.plugin.Plugin
import ru.able.router.messages.AdvancedPluginStart

import scala.util.Try

class ShowImage(canvas: CanvasFrame, name: String = "")
               (implicit mat: Materializer) extends Plugin with LazyLogging
{
  var pluginKillSwitch: Option[SharedKillSwitch] = None

  override def start(ps: AdvancedPluginStart): Unit =
    Try({
      pluginKillSwitch = Some(KillSwitches.shared("ShowImage"))
      logger.info("Starting image view")
      val (broadcast, killSwitch) = (ps.broadcast, ps.ks.sharedKillSwitch)

      var i = 0

      broadcast.mat
        .via(killSwitch.flow)
        .via(pluginKillSwitch.get.flow)
        .async
        .map(f => {
          val box_text   = "Streamer ====================================="
          val point      = new opencv_core.Point(50, i)
          i=i+1
          val scalar     = new opencv_core.Scalar(0, 255, 0, 2.0)
          val font       = FONT_HERSHEY_PLAIN
          putText(f.imgMat, box_text, point, font, 1.0, scalar)

          CameraFrame(f.imgMat, f.date)
        })
        .async
        .runWith(new ShowImageStage(canvas, name))
    }) recover {
      case e: Exception => logger.error(e.getMessage, e)
    }

  override def stop(): Unit = pluginKillSwitch match {
    case Some(ks) => ks.shutdown()
    case None     => logger.error("shutdown")
  }
}
