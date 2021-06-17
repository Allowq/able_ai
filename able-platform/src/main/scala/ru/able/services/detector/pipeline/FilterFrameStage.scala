package ru.able.services.detector.pipeline

import java.util.UUID

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.typesafe.scalalogging.LazyLogging

import ru.able.server.model.SocketFrame
import ru.able.server.controllers.flow.model.FrameSeqMessage
import ru.able.server.controllers.flow.protocol.SingularEvent
import ru.able.services.detector.model.{CanvasFrameSpecial, SignedFrame}

import scala.util.Try

class FilterFrameStage[Evt] extends GraphStage[FlowShape[Evt, SignedFrame]] with LazyLogging {
  private val in  = Inlet[Evt]("FilterFrameStage.in")
  private val out = Outlet[SignedFrame]("FilterFrameStage.out")

  private def converter(t: SocketFrame) = CanvasFrameSpecial.apply(t)

  override def shape: FlowShape[Evt, SignedFrame] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    implicit def mat: Materializer = this.materializer

    override def preStart(): Unit = {
      pull(in)
    }

    override def postStop(): Unit = {
    }

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        Try {
          processorStraight(grab[Evt](in))
        } recover {
          case e: Exception => logger.error("Error grabbing the camera frame: ", e)
        }
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = if (!hasBeenPulled(in)) pull(in)
    })

    private val processorStraight: Evt => Unit = { x: Evt => {
      val data: (UUID, Seq[CanvasFrameSpecial]) = x match {
        case SingularEvent(FrameSeqMessage(uuid, socketFrames)) => (uuid, socketFrames.map(converter))
      }
      data._2.foreach { cf: CanvasFrameSpecial => push(out, SignedFrame(data._1, cf)) }
    }}
  }
}
