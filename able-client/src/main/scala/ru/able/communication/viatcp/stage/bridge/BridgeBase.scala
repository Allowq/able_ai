package ru.able.communication.viatcp.stage.bridge

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{FlowShape, Inlet, Materializer, Outlet}
import akka.stream.scaladsl.{BidiFlow, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import akka.util.ByteString

import ru.able.communication.viatcp.protocol.{Command, Event, SingularCommand, SingularErrorEvent, SingularEvent, StreamEvent, StreamingCommand}
import ru.able.communication.viatcp.stage.ClientStage.{HostEvent, HostUp}
import ru.able.communication.viatcp.stage.bridge.BridgeBase.{EventException, IncorrectEventType}
import ru.able.communication.viatcp.stage.{ClientStage, Host, Processor, Resolver}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

object BridgeBase {
  val ProviderName = "TCPCommunication"

  trait ClientException

  case class InputQueueClosed() extends Exception with ClientException
  case class InputQueueUnavailable() extends Exception with ClientException
  case class IncorrectEventType[A](event: A) extends Exception with ClientException
  case class EventException[A](cause: A) extends Throwable

  private val _reconnectDuration = Duration(2, TimeUnit.SECONDS)
  private val _shouldReconnect = true

  def reconnectLogic[M](builder: GraphDSL.Builder[M],
                        hostEventSource: Source[HostEvent, NotUsed]#Shape,
                        hostEventIn: Inlet[HostEvent],
                        hostEventOut: Outlet[HostEvent])
                       (implicit system: ActorSystem) =
  {
    import GraphDSL.Implicits._
    implicit val b = builder

    val groupDelay = Flow[HostEvent]
      .groupBy[Host](1024, { x: HostEvent ⇒ x.host })
      .delay(_reconnectDuration)
      .map { x ⇒ system.log.warning(s"Reconnecting after ${_reconnectDuration.toSeconds}s for ${x.host}"); HostUp(x.host) }
      .mergeSubstreams

    if (_shouldReconnect) {
      val connectionMerge = builder.add(Merge[HostEvent](2))
      hostEventSource ~> connectionMerge ~> hostEventIn
      hostEventOut ~> b.add(groupDelay) ~> connectionMerge
    } else {
      hostEventSource ~> hostEventIn
      hostEventOut ~> Sink.ignore
    }
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

abstract class BridgeBase[Cmd, Evt]()(implicit system: ActorSystem, ec: ExecutionContext)
{
  type Context = Promise[Event[Evt]]

  protected val eventHandler = Sink.foreach[(Try[Event[Evt]], Context)] {
    case (Failure(msg), context) => context.failure(msg)
    case (Success(evt), context) => context.success(evt)
  }

  protected val g: RunnableGraph[SourceQueueWithComplete[(Command[Cmd], Context)]]

  protected val input: SourceQueueWithComplete[(Command[Cmd], Context)]

  protected def send(command: Command[Cmd]): Future[Event[Evt]]

  protected def ask(command: Cmd): Future[Evt] = send(SingularCommand(command)) flatMap {
    case SingularEvent(x)      ⇒ Future(x)
    case SingularErrorEvent(x) ⇒ Future.failed(EventException(x))
    case x                     ⇒ Future.failed(IncorrectEventType(x))
  }

  protected def askStream(command: Cmd): Future[Source[Evt, Any]] = send(SingularCommand(command)) flatMap {
    case StreamEvent(x)        ⇒ Future(x)
    case SingularErrorEvent(x) ⇒ Future.failed(EventException(x))
    case x                     ⇒ Future.failed(IncorrectEventType(x))
  }

  protected def sendStream(stream: Source[Cmd, Any]): Future[Evt] = send(StreamingCommand(stream)) flatMap {
    case SingularEvent(x)      ⇒ Future(x)
    case SingularErrorEvent(x) ⇒ Future.failed(EventException(x))
    case x                     ⇒ Future.failed(IncorrectEventType(x))
  }

  protected def sendStream(command: Cmd, stream: Source[Cmd, Any]): Future[Evt] = send(StreamingCommand(Source.single(command) ++ stream)) flatMap {
    case SingularEvent(x)      ⇒ Future(x)
    case SingularErrorEvent(x) ⇒ Future.failed(EventException(x))
    case x                     ⇒ Future.failed(IncorrectEventType(x))
  }

  protected def react(stream: Source[Cmd, Any]): Future[Source[Evt, Any]] = send(StreamingCommand(stream)) flatMap {
    case StreamEvent(x)        ⇒ Future(x)
    case SingularErrorEvent(x) ⇒ Future.failed(EventException(x))
    case x                     ⇒ Future.failed(IncorrectEventType(x))
  }
}
