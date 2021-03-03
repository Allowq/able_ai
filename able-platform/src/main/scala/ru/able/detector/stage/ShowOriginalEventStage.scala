package ru.able.detector.stage

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler}
import akka.stream.{Attributes, Inlet, SinkShape}
import com.typesafe.scalalogging.LazyLogging
import ru.able.detector.view.CanvasDetector

import scala.util.Try
import ru.able.server.protocol.{FrameSeqMessage, MessageFormat, SingularEvent}

object ShowOriginalEventStage {
  def apply[Evt](clientId: String): GraphStage[SinkShape[Evt]] = new ShowOriginalEventStage(clientId)
}

class ShowOriginalEventStage[Evt](val clientId: String = "default") extends GraphStage[SinkShape[Evt]] with LazyLogging
{
  private val in  = Inlet[Evt]("ShowImage.in")
  private val canvasDetector = new CanvasDetector()

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        grab(in) match {
          case SingularEvent(msg: MessageFormat) => processEvent(msg)
          case x                                 => println("Unhandled: " + x)
        }
        pull(in)
      }
    })

    private def processEvent(msg: MessageFormat): Unit = {
      Try {
        msg match {
          case FrameSeqMessage(uuid, socketFrames) => canvasDetector.updateCanvas(uuid, socketFrames)
        }
      } recover {
        case e: Throwable => logger.error(e.getMessage, e)
      }
    }

    override def preStart(): Unit = {
      pull(in)
    }
  }

  override def shape: SinkShape[Evt] = SinkShape(in)
}