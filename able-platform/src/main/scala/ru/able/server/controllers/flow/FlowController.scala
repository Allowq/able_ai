package ru.able.server.controllers.flow

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.stream.{BidiShape, FlowShape, RestartSettings}
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, Keep, Merge, RestartFlow, Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import ru.able.server.controllers.flow.ResolversFactory.BaseResolver
import ru.able.server.controllers.flow.model.FlowController.{BasicFT, DetectionFT, FlowType, ManagedDetectionFT}
import ru.able.server.controllers.flow.model.ResolversFactory.{BasicRT, FrameSeqRT}
import ru.able.server.controllers.flow.protocol.{Command, Event, MessageProtocol, ProducerAction, SingularCommand, SingularEvent, StreamEvent, StreamingCommand}
import ru.able.server.controllers.flow.stages.{ConsumerStage, ProducerStage}
import ru.able.services.detector.DetectorController

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class FlowController[Cmd, Evt](private val _detectorController: ActorRef)
                              (implicit system: ActorSystem, ec: ExecutionContext) extends LazyLogging
{
  def getFlowByType(flowType: FlowType): (ActorRef, Flow[ByteString, ByteString, Future[Done]]) = flowType match {
    case BasicFT => getBasicFlow
    case DetectionFT => getDetectionFlow
    case ManagedDetectionFT => getManagedDetectionFlow
  }

  private [server] def getBasicFlow: (ActorRef, Flow[ByteString, ByteString, Future[Done]]) = {
    val (publisher, source) = SourceActorPublisher.apply[Cmd]()(system)

    val restartingFlow = RestartFlow.withBackoff[ByteString, ByteString](
      RestartSettings(FiniteDuration(0, "seconds"), FiniteDuration(0, "seconds"), 1)
    ){ () =>
      Flow.fromGraph(GraphDSL.create() { implicit b =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val pipeline = b.add(
          getConsumeProduceBidiFlow(
            ResolversFactory(BasicRT),
            1,
            true
          ).atop[ByteString, ByteString, NotUsed](MessageProtocol())
        )

        source ~> pipeline.in1
        pipeline.out2 ~> Sink.ignore

        FlowShape(pipeline.in2, pipeline.out1)
      })
    }.watchTermination()(Keep.right)

    (publisher, restartingFlow)
  }

  private [server] def getDetectionFlow: (ActorRef, Flow[ByteString, ByteString, Future[Done]]) = {
    val detectionFlow = DetectorController.getDetectionFlow[Cmd, Evt](_detectorController)

    val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val pipeline = b.add(
        getConsumeProduceBidiFlow(
          ResolversFactory(FrameSeqRT),
          1,
          true
        ).atop[ByteString, ByteString, NotUsed](MessageProtocol())
      )
      val processing = b.add(detectionFlow)

      pipeline.out2 ~> processing ~> pipeline.in1

      FlowShape(pipeline.in2, pipeline.out1)
    }).watchTermination()(Keep.right)

    (Actor.noSender, flow)
  }

  private [server] def getManagedDetectionFlow: (ActorRef, Flow[ByteString, ByteString, Future[Done]]) =
  {
    val detectionFlow = DetectorController.getDetectionFlow[Cmd, Evt](_detectorController)
    val (publisher, source) = SourceActorPublisher.apply[Cmd]()(system)

    val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val pipeline = b.add(
        getConsumeProduceBidiFlow(
          ResolversFactory(FrameSeqRT),
          1,
          true
        ).atop[ByteString, ByteString, NotUsed](MessageProtocol())
      )
      val processing = b.add(detectionFlow)
      val commandInMerge = b.add(Merge[Command[Cmd]](2, true))

                                     commandInMerge.out ~> pipeline.in1
                           source ~> commandInMerge.in(0)
                       processing ~> commandInMerge.in(1)
      pipeline.out2 ~> processing

      FlowShape(pipeline.in2, pipeline.out1)
    }).watchTermination()(Keep.right)

    (publisher, flow)
  }

  private [server] def getConsumeProduceBidiFlow(resolver: BaseResolver[Evt],
                                                  producerParallism: Int,
                                                  shouldReact: Boolean): BidiFlow[Command[Cmd], Cmd, Evt, Event[Evt], Any] =
  {
    val consumerStage = new ConsumerStage[Evt, Cmd](resolver)
    val producerStage = new ProducerStage[Evt, Cmd]()

    val functionApply = Flow[(Event[Evt], ProducerAction[Evt, Cmd])].mapAsync[Command[Cmd]](producerParallism) {
      case (SingularEvent(evt), x: ProducerAction.Signal[Evt, Cmd])        => x.f(evt).map(SingularCommand[Cmd])
      case (SingularEvent(evt), x: ProducerAction.ProduceStream[Evt, Cmd]) => x.f(evt).map(StreamingCommand[Cmd])
      case (StreamEvent(evt), x: ProducerAction.ConsumeStream[Evt, Cmd])   => x.f(evt).map(SingularCommand[Cmd])
      case (StreamEvent(evt), x: ProducerAction.ProcessStream[Evt, Cmd])   => x.f(evt).map(StreamingCommand[Cmd])
    }

    BidiFlow.fromGraph[Command[Cmd], Cmd, Evt, Event[Evt], Any] {
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        val producer = b add producerStage
        val consumer = b add consumerStage
        val commandIn = b add Flow[Command[Cmd]]

        if (shouldReact) {
          val fa = b add functionApply
          val merge = b add Merge[Command[Cmd]](2, true)

                    commandIn ~> merge.in(0)
          consumer.out0 ~> fa ~> merge.in(1)
                                 merge.out ~> producer

        } else {
          consumer.out0 ~> Sink.ignore
          commandIn ~> producer
        }

        BidiShape(commandIn.in, producer.out, consumer.in, consumer.out1)
      }
    }
  }
}
