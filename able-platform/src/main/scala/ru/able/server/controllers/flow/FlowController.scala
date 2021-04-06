package ru.able.server.controllers.flow

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{BidiShape, FlowShape}
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, Keep, Merge, Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import ru.able.detector.DetectorController
import ru.able.server.controllers.flow.ResolversFactory.BaseResolver
import ru.able.server.controllers.flow.model.{BasicFT, BasicRT, DetectionFT, FlowType, FrameSeqRT, ManagedDetectionFT}
import ru.able.server.pipeline.{ConsumerStage, ProducerStage}
import ru.able.server.protocol.{Command, Event, ProducerAction, SimpleMessage, SingularCommand, SingularEvent, StreamEvent, StreamingCommand}

import scala.concurrent.{ExecutionContext, Future}

class FlowController[Cmd, Evt](implicit system: ActorSystem, ec: ExecutionContext) extends LazyLogging
{
  private val _detectorController = system.actorOf(DetectorController.props)

  def getFlowByType(flowType: FlowType): Flow[ByteString, ByteString, Future[Done]] = flowType match {
    case BasicFT => getBasicFlow
    case DetectionFT => getDetectionFlow
    case ManagedDetectionFT => getManagedDetectionFlow
  }

  private [server] def getBasicFlow: Flow[ByteString, ByteString, Future[Done]] = {
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val pipeline = b.add(
        getConsumerProduceBidiFlow(
          ResolversFactory(BasicRT),
          1,
          false
        ).atop[ByteString, ByteString, NotUsed](SimpleMessage())
      )

      pipeline.in1 <~ Source.empty
      pipeline.out2 ~> Sink.ignore

      FlowShape(pipeline.in2, pipeline.out1)
    }).watchTermination()(Keep.right)
  }

  private [server] def getDetectionFlow: Flow[ByteString, ByteString, Future[Done]] = {
    val detectionFlow = DetectorController.getDetectionFlow[Cmd, Evt](_detectorController)

    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val pipeline = b.add(
        getConsumerProduceBidiFlow(
          ResolversFactory(FrameSeqRT),
          1,
          true
        ).atop[ByteString, ByteString, NotUsed](SimpleMessage())
      )
      val processing = b.add(detectionFlow)

      pipeline.out2 ~> processing ~> pipeline.in1

      FlowShape(pipeline.in2, pipeline.out1)
    }).watchTermination()(Keep.right)
  }

  private [server] def getManagedDetectionFlow: Flow[ByteString, ByteString, Future[Done]] =
  {
    val detectionFlow = DetectorController.getDetectionFlow[Cmd, Evt](_detectorController)

    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val source = b.add(SourceActorPublisher.apply[Command[Cmd]](system))
      val pipeline = b.add(
        getConsumerProduceBidiFlow(
          ResolversFactory(FrameSeqRT),
          1,
          true
        ).atop[ByteString, ByteString, NotUsed](SimpleMessage())
      )
      val processing = b.add(detectionFlow)
      val commandInMerge = b.add(Merge[Command[Cmd]](2, true))

                                     commandInMerge.out ~> pipeline.in1
                           source ~> commandInMerge.in(0)
                       processing ~> commandInMerge.in(1)
      pipeline.out2 ~> processing

      FlowShape(pipeline.in2, pipeline.out1)
    }).watchTermination()(Keep.right)
  }

  private [server] def getConsumerProduceBidiFlow(resolver: BaseResolver[Evt],
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
