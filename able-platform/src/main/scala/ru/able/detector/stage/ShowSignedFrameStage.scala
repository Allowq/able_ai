package ru.able.detector.stage

import akka.stream.{Attributes, FlowShape, Inlet, Outlet, SinkShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.typesafe.scalalogging.LazyLogging
import ru.able.detector.model.SignedFrame
import ru.able.detector.view.CanvasDetector
import ru.able.server.protocol.{Command, MessageFormat, SimpleReply, SingularCommand, SingularEvent}
import ru.able.server.protocol.ConsumerAction.AcceptSignal

import scala.util.Try

class ShowSignedFrameStage[Cmd] extends GraphStage[FlowShape[SignedFrame, Cmd]] with LazyLogging
{
  private val in = Inlet[SignedFrame]("ShowImage.in")
  private val out = Outlet[Cmd]("ShowImage.out")
  private val canvasDetector = new CanvasDetector()

  override def shape: FlowShape[SignedFrame, Cmd] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    override def preStart(): Unit = pull(in)

    override def postStop(): Unit = { }

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        Try {
          val t = grab(in)
          t match {
            case SignedFrame(u, f) => {
              canvasDetector.updateCanvas(u, f)
//              push(out, SingularCommand[MessageFormat](SimpleReply("True")).asInstanceOf[Cmd])
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