package ru.able.server.controllers.flow

import java.net.InetSocketAddress

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorContext, ActorRef}
import akka.stream.{BidiShape, FlowShape, Materializer, RestartSettings}
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, Keep, Merge, RestartFlow, Sink}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import ru.able.server.controllers.flow.FlowFactory.ControlledFlow
import ru.able.server.controllers.flow.ResolversFactory.BaseResolver
import ru.able.server.controllers.flow.model.FlowTypes.{BasicFT, ExtendedFT, FlowType}
import ru.able.server.controllers.flow.model.ResolversFactory.{BasicRT, ExtendedRT}
import ru.able.server.controllers.flow.protocol.{Command, Event, MessageProtocol, ProducerAction, SingularCommand, SingularEvent, StreamEvent, StreamingCommand}
import ru.able.server.controllers.flow.stages.{CheckoutStage, ConsumerStage, ProducerStage, SourceFromActorStage}
import ru.able.services.detector.DetectorController
import ru.able.services.detector.pipeline.{DetectorStage, ShowSignedFrameStage}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object FlowFactory {
  type ControlledFlow = Tuple2[ActorRef, Flow[ByteString, ByteString, Future[Done]]]
}

final class FlowFactory[Cmd, Evt](implicit context: ActorContext, mat: Materializer, ec: ExecutionContext) extends LazyLogging
{
  def flow(flowType: FlowType)
          (rAddr: Option[InetSocketAddress], sessionKeeperActor: ActorRef = Actor.noSender)
  : ControlledFlow = flowType match
  {
    case BasicFT => basicFlow
    case ExtendedFT => detectionManagedFlow(rAddr, sessionKeeperActor)
  }

  private val _detectorController: ActorRef = DetectorController()
  private val _producerParallelism = 1

  private val functionApply = Flow[(Event[Evt], ProducerAction[Evt, Cmd])].mapAsync[Command[Cmd]](_producerParallelism) {
    case (SingularEvent(evt), x: ProducerAction.Signal[Evt, Cmd])        => x.f(evt).map(SingularCommand[Cmd])
    case (SingularEvent(evt), x: ProducerAction.ProduceStream[Evt, Cmd]) => x.f(evt).map(StreamingCommand[Cmd])
    case (StreamEvent(evt), x: ProducerAction.ConsumeStream[Evt, Cmd])   => x.f(evt).map(SingularCommand[Cmd])
    case (StreamEvent(evt), x: ProducerAction.ProcessStream[Evt, Cmd])   => x.f(evt).map(StreamingCommand[Cmd])
  }

  private def basicFlow: ControlledFlow =
  {
    val (actionPublisher, source) = SourceFromActorStage[Cmd](None)

    val restartingFlow = RestartFlow.withBackoff[ByteString, ByteString](
      RestartSettings(FiniteDuration(0, "seconds"), FiniteDuration(0, "seconds"), 1)
    ){ () =>
      Flow.fromGraph(GraphDSL.create() { implicit b =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val pipeline = b.add(
          getConsumeProduceBidiFlow(
            ResolversFactory(BasicRT),
            true
          ).atop[ByteString, ByteString, NotUsed](MessageProtocol())
        )

        source ~> pipeline.in1
                  pipeline.out2 ~> Sink.ignore

        FlowShape(pipeline.in2, pipeline.out1)
      })
    }.watchTermination()(Keep.right)

    (actionPublisher, restartingFlow)
  }

  private def getConsumeProduceBidiFlow(resolver: BaseResolver[Evt], shouldReact: Boolean)
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

  private def detectionManagedFlow(rAddrOpt: Option[InetSocketAddress], stageControlActor: ActorRef)
  : ControlledFlow =
  {
    val (actionPublisher, source) = SourceFromActorStage[Cmd](rAddrOpt)
    val restartingFlow = RestartFlow.withBackoff[ByteString, ByteString](
      RestartSettings(FiniteDuration(0, "seconds"), FiniteDuration(0, "seconds"), 1)
    )(
      () => detectionManagedShape(source, ResolversFactory(ExtendedRT))(rAddrOpt, stageControlActor)
    ).watchTermination()(Keep.right)

    (actionPublisher, restartingFlow)
  }

  private def detectionManagedShape(externalCommandPublisher: SourceFromActorStage[Cmd], resolver: BaseResolver[Evt])
                                   (rAddrOpt: Option[InetSocketAddress], stageControlActor: ActorRef)
  : Flow[ByteString, ByteString, Any] =
  {
    val (checkoutHandleActor, checkoutStage) = CheckoutStage[Evt, Cmd](rAddrOpt, stageControlActor)

    Flow.fromGraph[ByteString, ByteString, Any] {
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        val decode      = b.add(MessageProtocol.decoderFlow)
        val deserialize = b.add(MessageProtocol.deserializeFlow[Evt])
        val serialize   = b.add(MessageProtocol.serializeFlow)
        val encode      = b.add(MessageProtocol.encoderFlow)

        val checkout    = b.add(checkoutStage)
        val consumer    = b.add(new ConsumerStage[Evt, Cmd](resolver))
        val detector    = b.add(new DetectorStage(_detectorController).async)
        val frameShower = b.add(new ShowSignedFrameStage)
        val producer    = b.add(new ProducerStage[Evt, Cmd]())
        val fa          = b.add(functionApply)
        val commandInMerge = b.add(Merge[Command[Cmd]](4, true))

        checkout ~> consumer.in
                    consumer.out0 ~> fa ~>                           commandInMerge.in(0)
                    consumer.out1 ~> detector.in
                                     detector.out0 ~> frameShower ~> commandInMerge.in(1)
                                     detector.out1 ~>                commandInMerge.in(2)
        externalCommandPublisher  ~>                                 commandInMerge.in(3)
                                                                     commandInMerge.out ~> producer

        decode ~> deserialize ~> checkout
        /* checkout ~> processing ~> producer */
        producer ~> serialize ~> encode

        FlowShape(decode.in, encode.out)
      }
    }
  }
}
