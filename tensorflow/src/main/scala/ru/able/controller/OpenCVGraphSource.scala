package ru.able.controller

import akka.stream.javadsl.RunnableGraph
import akka.stream.{Attributes, Materializer, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import org.bytedeco.javacv.{Frame, OpenCVFrameGrabber}

private class CameraSource(cameraDeviceIdx: Int)
                          (implicit materializer: Materializer) extends GraphStage[SourceShape[Frame]] {
  val output = Outlet[Frame]("OpenCVSource")
  override def shape: SourceShape[Frame] = SourceShape(output)

  private def buildGrabber(): OpenCVFrameGrabber = synchronized
  {
    val g = new OpenCVFrameGrabber(cameraDeviceIdx)
    g.start()
    g
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {

      private lazy val grabber = buildGrabber()

      override def postStop(): Unit = {
        super.postStop()
        Option(grabber).foreach(_.close())
      }

      setHandler(output, this)

      override def onPull(): Unit =
        grabFrame().foreach(push(output, _))

      private def grabFrame(): Option[Frame] =
        Option(grabber.grab())
    }
}