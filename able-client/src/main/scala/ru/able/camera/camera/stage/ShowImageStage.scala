package ru.able.camera.camera.stage

import java.util.concurrent.Executors

import akka.stream._
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.InHandler
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacv.CanvasFrame
import org.bytedeco.javacv.OpenCVFrameConverter.ToIplImage
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import ru.able.camera.camera.CameraFrame

/**
  * Sink to display Frames on canvas
  *
  * @param canvas    a JFrame that displays the given frame
  * @param converter converts frame to CanvasFrame
  */
class ShowImageStage(canvas: CanvasFrame, converter: ToIplImage, name: String = "")
  extends GraphStage[SinkShape[CameraFrame]] with LazyLogging
{
  private val in  = Inlet[CameraFrame]("ShowImage.in")
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))

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

    private def showImage(elem: CameraFrame) =
      Future {
          Try {
            println(s"$name ${elem.date}")
            canvas.showImage(converter.convert(elem.image))
          } recover {
            case e: Throwable => logger.error(e.getMessage, e)
          }
      }

    override def preStart(): Unit = pull(in)
  }

  override def shape: SinkShape[CameraFrame] = SinkShape(in)
}
