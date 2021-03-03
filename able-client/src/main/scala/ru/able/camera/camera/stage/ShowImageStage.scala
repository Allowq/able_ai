package ru.able.camera.camera.stage

import akka.stream._
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.InHandler
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacv.CanvasFrame
import scala.util.Try

import ru.able.camera.camera.CameraFrame
import ru.able.camera.utils.MediaConversion

class ShowImageStage(canvas: CanvasFrame, name: String = "") extends GraphStage[SinkShape[CameraFrame]] with LazyLogging
{
  private val in  = Inlet[CameraFrame]("ShowImage.in")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(
      in,
      new InHandler {
        override def onPush(): Unit = {
          showImage(grab(in))
          pull(in)
        }
      }
    )

    private def showImage(elem: CameraFrame): Unit = {
      Try {
        logger.debug(s"$name ${elem.date}")
        canvas.showImage(MediaConversion.toFrame(elem.imgMat))
      } recover {
        case e: Throwable => logger.error(e.getMessage, e)
      }
    }

    override def preStart(): Unit = pull(in)
  }

  override def shape: SinkShape[CameraFrame] = SinkShape(in)
}
