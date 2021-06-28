package ru.able.server.controllers.flow

import java.net.InetSocketAddress

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorContext, ActorRef}
import akka.stream.{BidiShape, FlowShape, Materializer, RestartSettings}
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, Keep, Merge, RestartFlow, Sink}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import ru.able.server.controllers.flow.ResolversFactory.BaseResolver
import ru.able.server.controllers.flow.model.FlowTypes.{BasicFT, DetectionFT, ExtendedFT, FlowType, ManagedDetectionFT}
import ru.able.server.controllers.flow.model.ResolversFactory.{BasicRT, ExtendedRT, FrameSeqRT}
import ru.able.server.controllers.flow.protocol.{Command, Event, MessageProtocol, ProducerAction, SingularCommand, SingularEvent, StreamEvent, StreamingCommand}
import ru.able.server.controllers.flow.stages.{CheckoutStage, ConsumerStage, ProducerStage, SourceActorPublisher}
import ru.able.services.detector.DetectorController

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

final class FlowFactory[Cmd, Evt](implicit context: ActorContext, mat: Materializer, ec: ExecutionContext) extends LazyLogging
{
  def flow(flowType: FlowType)
          (rAddr: InetSocketAddress = new InetSocketAddress(1337), sessionKeeperActor: ActorRef = Actor.noSender)
  : (ActorRef, Flow[ByteString, ByteString, Future[Done]]) = flowType match
  {
    case BasicFT => getBasicFlow
    case ExtendedFT => getExtendedFlow(rAddr, sessionKeeperActor)
    case DetectionFT => getDetectionFlow
    case ManagedDetectionFT => getManagedDetectionFlow
  }

  private val _detectorController: ActorRef = DetectorController()
  private val _producerParallism = 1

  private val functionApply = Flow[(Event[Evt], ProducerAction[Evt, Cmd])].mapAsync[Command[Cmd]](_producerParallism) {
    case (SingularEvent(evt), x: ProducerAction.Signal[Evt, Cmd])        => x.f(evt).map(SingularCommand[Cmd])
    case (SingularEvent(evt), x: ProducerAction.ProduceStream[Evt, Cmd]) => x.f(evt).map(StreamingCommand[Cmd])
    case (StreamEvent(evt), x: ProducerAction.ConsumeStream[Evt, Cmd])   => x.f(evt).map(SingularCommand[Cmd])
    case (StreamEvent(evt), x: ProducerAction.ProcessStream[Evt, Cmd])   => x.f(evt).map(StreamingCommand[Cmd])
  }

  private def getBasicFlow: (ActorRef, Flow[ByteString, ByteString, Future[Done]]) = {
    val (publisher, source) = SourceActorPublisher.apply[Cmd](context)

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

  private def getDetectionFlow: (ActorRef, Flow[ByteString, ByteString, Future[Done]]) = {
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

  private def getManagedDetectionFlow: (ActorRef, Flow[ByteString, ByteString, Future[Done]]) =
  {
    val detectionFlow = DetectorController.getDetectionFlow[Cmd, Evt](_detectorController)
    val (publisher, source) = SourceActorPublisher.apply[Cmd](context)

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

      pipeline.out2 ~> processing
                       processing ~> commandInMerge.in(0)
                           source ~> commandInMerge.in(1)
                                     commandInMerge.out ~> pipeline.in1

      FlowShape(pipeline.in2, pipeline.out1)
    }).watchTermination()(Keep.right)

    (publisher, flow)
  }

  private def getConsumeProduceBidiFlow(resolver: BaseResolver[Evt],
                                        producerParallism: Int,
                                        shouldReact: Boolean)
  : BidiFlow[Command[Cmd], Cmd, Evt, Event[Evt], Any] =
  {
    val consumerStage = new ConsumerStage[Evt, Cmd](resolver)
    val producerStage = new ProducerStage[Evt, Cmd]()

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

  private def getExtendedFlow(rAddr: InetSocketAddress, sessionKeeperActor: ActorRef)
  : (ActorRef, Flow[ByteString, ByteString, Future[Done]]) =
  {
    val (publisher, source) = SourceActorPublisher.apply[Cmd](context)
    val detectionFlow = DetectorController.getDetectionFlow[Cmd, Evt](_detectorController)
    val restartingFlow = RestartFlow.withBackoff[ByteString, ByteString](
      RestartSettings(FiniteDuration(0, "seconds"), FiniteDuration(0, "seconds"), 1)
    ){ () =>
      Flow.fromGraph(GraphDSL.create() { implicit b =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val pipeline = b.add(
          getBasisBidiFlow(ResolversFactory(ExtendedRT), 1)(rAddr, sessionKeeperActor)
            .atop[ByteString, ByteString, NotUsed](MessageProtocol())
        )
        val detector = b.add(detectionFlow)
        val commandInMerge = b.add(Merge[Command[Cmd]](2, true))

        pipeline.out2 ~> detector
                         detector ~> commandInMerge.in(0)
                           source ~> commandInMerge.in(1)
                                     commandInMerge.out ~> pipeline.in1

        FlowShape(pipeline.in2, pipeline.out1)
      })
    }.watchTermination()(Keep.right)

    (publisher, restartingFlow)
  }

  private def getBasisBidiFlow(resolver: BaseResolver[Evt], producerParallism: Int)
                                             (rAddr: InetSocketAddress, stageControlActor: ActorRef)
  : BidiFlow[Command[Cmd], Cmd, Evt, Event[Evt], Any] =
  {
    val checkoutStage = new CheckoutStage[Evt](rAddr, stageControlActor)
    val consumerStage = new ConsumerStage[Evt, Cmd](resolver)
    val producerStage = new ProducerStage[Evt, Cmd]()

    BidiFlow.fromGraph[Command[Cmd], Cmd, Evt, Event[Evt], Any] {
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        val checkout = b add checkoutStage
        val consumer = b add consumerStage
        val producer = b add producerStage
        val commandIn = b add Flow[Command[Cmd]]
        val fa = b add functionApply
        val merge = b add Merge[Command[Cmd]](2, true)

        checkout ~> consumer.in
                    consumer.out0 ~> fa ~> merge.in(1)
                              commandIn ~> merge.in(0)
                                           merge.out ~> producer

        BidiShape(commandIn.in, producer.out, checkout.in, consumer.out1)
      }
    }
  }
}
