package ru.able.controller

import akka.stream.{Attributes, Materializer, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import org.bytedeco.javacv.{FFmpegFrameGrabber, Frame}
import org.bytedeco.javacv.FrameGrabber.ImageMode
import org.bytedeco.javacpp.opencv_core.CV_8U

private class FFmpegSource(pathToVideo: String)(implicit mat: Materializer) extends GraphStage[SourceShape[Frame]] {
  val output = Outlet[Frame]("FFmpegSpurce")
  override def shape: SourceShape[Frame] = SourceShape(output)

  private def buildGrabber(bitsPerPixel: Int = CV_8U,
                           imageMode: ImageMode = ImageMode.COLOR,
                           imageHeight: Int = 720,
                           imageWidth: Int = 1280): FFmpegFrameGrabber = synchronized
  {
    val g = new FFmpegFrameGrabber(pathToVideo)
    g.setImageWidth(imageWidth)
    g.setImageHeight(imageHeight)
    g.setBitsPerPixel(bitsPerPixel)
    g.setImageMode(imageMode)
    g.setFrameRate(29.32)
    g.start()
    g
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {

      private var grabber: FFmpegFrameGrabber = _

      override def preStart(): Unit = {
        super.preStart()
        grabber = buildGrabber()
      }

      override def postStop(): Unit = {
        super.postStop()
        Option(grabber).foreach(_.close())
      }

      setHandler(output, this)

      override def onPull(): Unit =
        grabFrame().foreach(
          frame => push(output, frame)
        )

      private def grabFrame(): Option[Frame] =
        Option(grabber.grab())
    }
}
