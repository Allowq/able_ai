package ru.able.communication.viatcp.model

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BidiFlow, Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{FlowShape, Inlet, Materializer, Outlet, OverflowStrategy}
import akka.util.ByteString

import ru.able.camera.utils.settings.Settings
import ru.able.communication.viatcp.TCPCommunication
import ru.able.communication.viatcp.protocol.{Command, Event}
import ru.able.communication.viatcp.stage.ClientStage.{HostEvent, HostUp}
import ru.able.communication.viatcp.stage.{ClientStage, Host, Processor, Resolver}

import scala.concurrent.duration.{Duration, FiniteDuration}
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
    val processor = Processor[Cmd, Evt](resolver, settings.producerParallelismValue)

    new TCPCommunication(
      hosts,
      settings.maxConnectionsPerHost,
      settings.maxFailuresPerHost,
      settings.failureRecoveryPeriod,
      settings.inputBufferSize,
      settings.clientUUID,
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
    val processor = Processor[Cmd, Evt](resolver, settings.producerParallelismValue)
    new TCPCommunication(
      Source(hosts.map(HostUp)),
      settings.maxConnectionsPerHost,
      settings.maxFailuresPerHost,
      settings.failureRecoveryPeriod,
      settings.inputBufferSize,
      settings.clientUUID,
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

  def reconnectLogic[M](builder: GraphDSL.Builder[M],
                        hostEventSource: Source[HostEvent, NotUsed]#Shape,
                        hostEventIn: Inlet[HostEvent],
                        hostEventOut: Outlet[HostEvent],
                        reconnectDuration: FiniteDuration = Duration(2, TimeUnit.SECONDS),
                        shouldReconnect: Boolean = true)
                       (implicit system: ActorSystem) =
  {
    import GraphDSL.Implicits._
    implicit val b = builder

    val groupDelay = Flow[HostEvent]
      .groupBy[Host](1024, { x: HostEvent ⇒ x.host })
      .delay(reconnectDuration)
      .map { x ⇒ system.log.warning(s"Reconnecting after ${reconnectDuration.toSeconds}s for ${x.host}"); HostUp(x.host) }
      .mergeSubstreams

    if (shouldReconnect) {
      val connectionMerge = builder.add(Merge[HostEvent](2))
      hostEventSource ~> connectionMerge ~> hostEventIn
      hostEventOut ~> b.add(groupDelay) ~> connectionMerge
    } else {
      hostEventSource ~> hostEventIn
      hostEventOut ~> Sink.ignore
    }
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
