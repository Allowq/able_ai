package ru.able.detector

import akka.actor.ActorRef
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL}
import ru.able.detector.stage.{DetectStage, FilterFrameStage, ShowSignedFrameStage}
import ru.able.server.protocol.{Command, Event}

case class Detector[Cmd, Evt](flow: Flow[Event[Evt], Command[Cmd], Any])

object Detector {
  def apply[Cmd, Evt](detectController: ActorRef): Detector[Cmd, Evt] = {
    Detector(Flow.fromGraph[Event[Evt], Command[Cmd], Any] {
      GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._

        val pipelineIn = b.add(new FilterFrameStage[Event[Evt]])
        val pipelineOut = b.add(new ShowSignedFrameStage[Command[Cmd]])

        pipelineIn ~> b.add(new DetectStage(detectController).async) ~> pipelineOut

        FlowShape(pipelineIn.in, pipelineOut.out)
      }
    })
  }
}
