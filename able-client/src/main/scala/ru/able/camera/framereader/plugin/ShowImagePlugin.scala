package ru.able.camera.framereader.plugin

import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_core.FONT_HERSHEY_PLAIN
import org.bytedeco.javacpp.opencv_imgproc.putText
import org.bytedeco.javacv.CanvasFrame
import ru.able.camera.framereader.plugin.stage.ShowImageStage
import ru.able.camera.framereader.model.CameraFrame
import ru.able.router.model.{AdvancedPluginStart, Plugin}

import scala.util.Try

class ShowImagePlugin(canvas: CanvasFrame)(implicit mat: Materializer) extends Plugin with LazyLogging
{
  var pluginKillSwitch: Option[SharedKillSwitch] = None

  override def start(ps: AdvancedPluginStart): Unit = {
    Try {
      pluginKillSwitch = Some(KillSwitches.shared("ShowImage"))
      val (broadcast, killSwitch) = (ps.broadcast, ps.ks.sharedKillSwitch)

      broadcast.mat
        .via(killSwitch.flow)
        .via(pluginKillSwitch.get.flow)
        .async
        .map(printPluginId)
        .runWith(new ShowImageStage(canvas))
    } recover {
      case e: Exception => logger.error(e.getMessage, e)
    }
  }

  override def stop(): Unit = pluginKillSwitch match {
    case Some(ks) => ks.shutdown()
    case None     => logger.error("shutdown")
  }

  private def printPluginId(cf: CameraFrame): CameraFrame = {
    val imgMat     = cf.imgMat
    val box_text   = cf.date.toString
    val point      = new opencv_core.Point(50, 40)
    val scalar     = new opencv_core.Scalar(0, 255, 0, 2.0)
    val font       = FONT_HERSHEY_PLAIN
    putText(imgMat, box_text, point, font, 1.0, scalar)
    CameraFrame(imgMat, cf.date)
  }
}
