package ru.able.server.controllers.flow

import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.{Actor, ActorContext, ActorRef}
import akka.stream.{BidiShape, FlowShape, Materializer, RestartSettings}
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, Keep, Merge, RestartFlow, Sink}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import ru.able.server.controllers.flow.ResolverFactory.BaseResolver
import ru.able.server.controllers.flow.model.FlowTypes.{ControlledFlow, SimplePublishFlow}
import ru.able.server.controllers.flow.model.ResolversFactory.{BasicRT, ExtendedRT}
import ru.able.server.controllers.flow.protocol.{Command, Event, MessageProtocol, ProducerAction, SingularCommand, SingularEvent, StreamEvent, StreamingCommand}
import ru.able.server.controllers.flow.stages.{CheckoutStage, ConsumerStage, ProducerStage, SourceFromActorStage}
import ru.able.services.detector.pipeline.{DetectorStage, ShowSignedFrameStage}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext

final class FlowFactory[Cmd, Evt](implicit context: ActorContext, mat: Materializer, ec: ExecutionContext) extends LazyLogging
{
  private val _producerParallelism = 1

  private val _functionApply = Flow[(Event[Evt], ProducerAction[Evt, Cmd])].mapAsync[Command[Cmd]](_producerParallelism) {
    case (SingularEvent(evt), x: ProducerAction.Signal[Evt, Cmd])        => x.f(evt).map(SingularCommand[Cmd])
    case (SingularEvent(evt), x: ProducerAction.ProduceStream[Evt, Cmd]) => x.f(evt).map(StreamingCommand[Cmd])
    case (StreamEvent(evt), x: ProducerAction.ConsumeStream[Evt, Cmd])   => x.f(evt).map(SingularCommand[Cmd])
    case (StreamEvent(evt), x: ProducerAction.ProcessStream[Evt, Cmd])   => x.f(evt).map(StreamingCommand[Cmd])
  }

  def basicFlow: SimplePublishFlow =
  {
    val (commandPublisherActor, commandPublisherSource) = SourceFromActorStage[Cmd](None)

    val restartingFlow = RestartFlow.withBackoff[ByteString, ByteString](
      RestartSettings(FiniteDuration(0, "seconds"), FiniteDuration(0, "seconds"), 1)
    ){ () =>
      Flow.fromGraph(GraphDSL.create() { implicit b =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val pipeline = b.add(
          getConsumeProduceBidiFlow(
            ResolverFactory(BasicRT),
            true
          ).atop[ByteString, ByteString, NotUsed](MessageProtocol())
        )

        commandPublisherSource ~> pipeline.in1
                                  pipeline.out2 ~> Sink.ignore

        FlowShape(pipeline.in2, pipeline.out1)
      })
    }.watchTermination()(Keep.right)

    (commandPublisherActor, restartingFlow)
  }

  def detectionManagedFlow(rAddrOpt: Option[InetSocketAddress] = None, stageControlActor: ActorRef = Actor.noSender)
  : ControlledFlow =
  {
    val (commandPublisherActor, commandPublisherSource) = SourceFromActorStage[Cmd](rAddrOpt)
    val (checkoutHandleActor, checkoutHandleFlow) = CheckoutStage[Evt, Cmd](rAddrOpt, stageControlActor)

    val restartingFlow = RestartFlow.withBackoff[ByteString, ByteString](
      RestartSettings(FiniteDuration(0, "seconds"), FiniteDuration(0, "seconds"), 1)
    ){
      () => detectionManagedShape(commandPublisherSource, checkoutHandleFlow, ResolverFactory(ExtendedRT))
    }.watchTermination()(Keep.right)

    (checkoutHandleActor, commandPublisherActor, restartingFlow)
  }

  private def getConsumeProduceBidiFlow(resolver: BaseResolver[Evt], shouldReact: Boolean)
  : BidiFlow[Command[Cmd], Cmd, Evt, Event[Evt], Any] =
  {
    val consumerStage = new ConsumerStage[Evt, Cmd](resolver)
    val producerStage = new ProducerStage[Evt, Cmd]()

    BidiFlow.fromGraph[Command[Cmd], Cmd, Evt, Event[Evt], Any] {
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        val producer = b.add(producerStage)
        val consumer = b.add(consumerStage)
        val commandIn = b.add(Flow[Command[Cmd]])

        if (shouldReact) {
          val fa = b.add(_functionApply)
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

  private def detectionManagedShape(externalCommandPublisherSource: SourceFromActorStage[Cmd],
                                    checkoutHandleFlow: CheckoutStage[Evt, Cmd],
                                    resolver: BaseResolver[Evt])
  : Flow[ByteString, ByteString, Any] =
  {
    Flow.fromGraph[ByteString, ByteString, Any] {
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        val decode      = b.add(MessageProtocol.decoderFlow)
        val deserialize = b.add(MessageProtocol.deserializeFlow)
        val serialize   = b.add(MessageProtocol.serializeFlow)
        val encode      = b.add(MessageProtocol.encoderFlow)

        val checkout    = b.add(checkoutHandleFlow)
        val consumer    = b.add(new ConsumerStage[Evt, Cmd](resolver))
        val detector    = b.add(new DetectorStage[Evt, Command[Cmd]])
        val frameShower = b.add(new ShowSignedFrameStage)
        val producer    = b.add(new ProducerStage[Evt, Cmd]())
        val fa          = b.add(_functionApply)
        val commandInMerge = b.add(Merge[Command[Cmd]](4, true))

        checkout ~> consumer.in
                    consumer.out0 ~> fa ~>                           commandInMerge.in(0)
                    consumer.out1 ~> detector.in
                                     detector.out0 ~> frameShower ~> commandInMerge.in(1)
                                     detector.out1 ~>                commandInMerge.in(2)
        externalCommandPublisherSource  ~>                           commandInMerge.in(3)
                                                                     commandInMerge.out ~> producer

        decode ~> deserialize ~> checkout
        /* checkout ~> processing ~> producer */
        producer ~> serialize ~> encode

        FlowShape(decode.in, encode.out)
      }
    }
  }

  private def encodeSerializeFlow(): Flow[Cmd, ByteString, Any] = {
    Flow.fromGraph[Cmd, ByteString, Any] {
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        val serialize   = b.add(MessageProtocol.serializeFlow)
        val encode      = b.add(MessageProtocol.encoderFlow)

        serialize ~> encode

        FlowShape(serialize.in, encode.out)
      }
    }
  }

  private def decodeDeserializeFlow(): Flow[ByteString, Evt, Any] = {
    Flow.fromGraph[ByteString, Evt, Any] {
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        val decode      = b.add(MessageProtocol.decoderFlow)
        val deserialize = b.add(MessageProtocol.deserializeFlow[Evt])

        decode ~> deserialize

        FlowShape(decode.in, deserialize.out)
      }
    }
  }
}
