package ru.able.controller

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Materializer, Outlet, SourceShape}
import org.bytedeco.javacv.{FFmpegFrameGrabber, Frame}
import ru.able.utils.settings.PropertyBasedSettings

private class CatchSource[T <: PropertyBasedSettings](sourceSettings: T, sourceType: String)
                                                     (implicit mat: Materializer) extends GraphStage[SourceShape[Frame]] {
  val output = Outlet[Frame]("CatchedSource")
  override def shape: SourceShape[Frame] = SourceShape(output)

  private def buildGrabber: FFmpegFrameGrabber = synchronized
  {
    val g = new FFmpegFrameGrabber(sourceType)
    g.setFormat(sourceSettings.getString("format"))
    g.setFrameRate(sourceSettings.getDouble("frameRate"))
    g.setImageHeight(sourceSettings.getInt("height"))
    g.setImageWidth(sourceSettings.getInt("width"))
    g.start()
    g
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {

      private var _grabber: FFmpegFrameGrabber = _

      override def preStart(): Unit = {
        super.preStart()
        _grabber = buildGrabber
      }

      override def postStop(): Unit = {
        super.postStop()
        Option(_grabber).foreach(_.close())
      }

      setHandler(output, this)

      override def onPull(): Unit =
        grabFrame().foreach(push(output, _))

      private def grabFrame(): Option[Frame] =
        Option(_grabber.grab())
    }
}
