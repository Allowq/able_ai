package ru.able.communication.viatcp.model

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BidiFlow, Broadcast, Flow, GraphDSL, Sink, Source}
import akka.stream.{FlowShape, Materializer, OverflowStrategy}
import akka.util.ByteString
import ru.able.camera.utils.settings.Settings
import ru.able.communication.viatcp.TCPCommunication
import ru.able.communication.viatcp.TCPCommunication.reconnectLogic
import ru.able.communication.viatcp.protocol.{Command, Event}
import ru.able.communication.viatcp.stage.ClientStage.{HostEvent, HostUp}
import ru.able.communication.viatcp.stage.{ClientStage, Host, Processor, Resolver}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.Try

object TCPCommunicationBase
{
  trait ClientException

  case class InputQueueClosed() extends Exception with ClientException
  case class InputQueueUnavailable() extends Exception with ClientException
  case class IncorrectEventType[A](event: A) extends Exception with ClientException
  case class EventException[A](cause: A) extends Throwable

  def apply[Cmd, Evt](hosts: Source[HostEvent, NotUsed],
                      settings: Settings,
                      resolver: Resolver[Evt],
                      shouldReact: Boolean,
                      inputOverflowStrategy: OverflowStrategy,
                      protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any])
                     (implicit system: ActorSystem, ec: ExecutionContext)
  : TCPCommunication[Cmd, Evt] =
  {
    val processor = Processor[Cmd, Evt](resolver, settings.getProducerParallelism)

    new TCPCommunication(
      hosts,
      settings.getMaxConnectionsPerHost,
      settings.getMaxFailuresPerHost,
      settings.getFailureRecoveryPeriod,
      settings.getInputBufferSize,
      inputOverflowStrategy,
      processor,
      protocol.reversed)
  }

  def apply[Cmd, Evt](hosts: List[Host],
                      settings: Settings,
                      resolver: Resolver[Evt],
                      shouldReact: Boolean,
                      inputOverflowStrategy: OverflowStrategy,
                      protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any])
                     (implicit system: ActorSystem, ec: ExecutionContext)
  : TCPCommunication[Cmd, Evt] =
  {
    val processor = Processor[Cmd, Evt](resolver, settings.getProducerParallelism)
    new TCPCommunication(
      Source(hosts.map(HostUp)),
      settings.getMaxConnectionsPerHost,
      settings.getMaxFailuresPerHost,
      settings.getFailureRecoveryPeriod,
      settings.getInputBufferSize,
      inputOverflowStrategy,
      processor,
      protocol.reversed)
  }

  def rawFlow[Context, Cmd, Evt](hosts: Source[HostEvent, NotUsed],
                                 maxConnectionPerHost: Int,
                                 maxFailuresPerHost: Int,
                                 failureRecoveryPeriod: FiniteDuration,
                                 producerParallelism: Int,
                                 resolver: Resolver[Evt],
                                 shouldReact: Boolean = false,
                                 protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any])
                                (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
  : Flow[(Command[Cmd], Context), HostEvent, NotUsed] =
  {
    val processor = Processor[Cmd, Evt](resolver, producerParallelism)

    Flow.fromGraph(GraphDSL.create(hosts) { implicit b ⇒
      connections ⇒

        val s = b.add(
          new ClientStage[Context, Cmd, Evt](
            maxConnectionPerHost,
            maxFailuresPerHost,
            failureRecoveryPeriod,
            true,
            processor,
            protocol.reversed
          )
        )

        reconnectLogic(b, connections, s.in2, s.out2)

        FlowShape(s.in1, s.out2)
    })
  }

  def flow[Cmd, Evt](hosts: Source[HostEvent, NotUsed],
                     maxConnectionPerHost: Int,
                     maxFailuresPerHost: Int,
                     failureRecoveryPeriod: FiniteDuration,
                     producerParallelism: Int,
                     clientParallelism: Int,
                     resolver: Resolver[Evt],
                     shouldReact: Boolean = false,
                     protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any])
                    (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
  : Flow[Command[Cmd], Event[Evt], NotUsed] =
  {
    val processor = Processor[Cmd, Evt](resolver, producerParallelism)
    type Context = Promise[Event[Evt]]

    val eventHandler = Sink.foreach[(Try[Event[Evt]], Promise[Event[Evt]])] {
      case (evt, context) ⇒ context.complete(evt)
    }

    Flow.fromGraph(GraphDSL.create(hosts) { implicit b ⇒
      connections ⇒
        import GraphDSL.Implicits._

        val s = b.add(
          new ClientStage[Context, Cmd, Evt](
            maxConnectionPerHost,
            maxFailuresPerHost,
            failureRecoveryPeriod,
            true,
            processor,
            protocol.reversed
          )
        )

        reconnectLogic(b, connections, s.in2, s.out2)

        val input = b add Flow[Command[Cmd]].map(x ⇒ (x, Promise[Event[Evt]]()))
        val broadcast = b add Broadcast[(Command[Cmd], Promise[Event[Evt]])](2)

        val output = b add Flow[(Command[Cmd], Promise[Event[Evt]])].mapAsync(clientParallelism)(_._2.future)

        s.out1 ~> eventHandler
        input ~> broadcast
        broadcast ~> output
        broadcast ~> s.in1

        FlowShape(input.in, output.out)
    })
  }
}
