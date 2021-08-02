package ru.able.services.detector.pipeline

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.typesafe.scalalogging.LazyLogging
import ru.able.services.detector.DetectorView
import ru.able.services.detector.model.SignedFrame

import scala.util.Try

class ShowSignedFrameStage[Cmd] extends GraphStage[FlowShape[SignedFrame, Cmd]] with LazyLogging
{
  private val in = Inlet[SignedFrame]("ShowImage.in")
  private val out = Outlet[Cmd]("ShowImage.out")
  private val canvasDetector = new DetectorView()

  override def shape: FlowShape[SignedFrame, Cmd] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    override def preStart(): Unit = pull(in)

    override def postStop(): Unit = canvasDetector.canvas.dispose()

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        Try {
          val t = grab(in)
          t match {
            case SignedFrame(u, f) => {
              canvasDetector.updateCanvas(u, f)
              pull(in)
            }
            case _ => println("wtf?")
          }
        } recover { case e: Throwable => logger.error(e.getMessage, e) }
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = if (!hasBeenPulled(in)) pull(in)
    })
  }
}