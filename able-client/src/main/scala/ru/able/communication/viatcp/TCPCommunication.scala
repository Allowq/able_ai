package ru.able.communication.viatcp

import java.util.concurrent.{ExecutorService, TimeUnit}

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{ClosedShape, Inlet, Outlet, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success, Try}
import java.util.UUID

import ru.able.camera.framereader.model.CameraFrame
import ru.able.camera.utils.settings.Settings
import ru.able.communication.viasocket.{SocketFrame, SocketFrameConverter}
import ru.able.communication.viatcp.TCPCommunication._
import ru.able.communication.viatcp.model.TCPCommunicationBase._
import ru.able.communication.viatcp.stage.ClientStage.{HostEvent, HostUp}
import ru.able.communication.viatcp.stage.{ClientStage, Host, Processor, Resolver}
import ru.able.communication.viatcp.protocol.{Command, Event, FrameSeqMessage, MessageFormat, SingularCommand, SingularErrorEvent, SingularEvent, StreamEvent, StreamingCommand}

object TCPCommunication {
  val ProviderName                = "TCPCommunication"
  private val _reconnectDuration  = Duration(2, TimeUnit.SECONDS)
  private val _shouldReconnect    = true

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

  def props[Cmd, Evt](settings: Settings,
                      resolver: Resolver[Evt],
                      protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any])
                     (implicit system: ActorSystem, ec: ExecutionContext) =
  {
    Props(
      new TCPCommunication[Cmd, Evt](
        Source.single(HostUp(Host(settings.getNetworkClientHost, settings.getNetworkClientPort))),
        settings.getMaxConnectionsPerHost,
        settings.getMaxFailuresPerHost,
        settings.getFailureRecoveryPeriod,
        settings.getInputBufferSize,
        OverflowStrategy.backpressure,
        Processor[Cmd, Evt](resolver, settings.getClientParallelism)(ec),
        protocol.reversed)
    )
  }
}

class TCPCommunication[Cmd, Evt](hosts: Source[HostEvent, NotUsed],
                                 connectionsPerHost: Int,
                                 maximumFailuresPerHost: Int,
                                 recoveryPeriod: FiniteDuration,
                                 inputBufferSize: Int,
                                 inputOverflowStrategy: OverflowStrategy,
                                 processor: Processor[Cmd, Evt],
                                 protocol: BidiFlow[ByteString, Evt, Cmd, ByteString, Any])
                                (implicit system: ActorSystem, ec: ExecutionContext) extends ReactiveTCP[Cmd, Evt]
{
  type Context = Promise[Event[Evt]]

  val eventHandler = Sink.foreach[(Try[Event[Evt]], Context)] {
    case (Failure(msg), context) => context.failure(msg)
    case (Success(evt), context) => context.success(evt)
  }

  val g = RunnableGraph.fromGraph(
    GraphDSL.create(Source.queue[(Command[Cmd], Context)](inputBufferSize, inputOverflowStrategy)) {
      implicit b =>
        source =>
          import GraphDSL.Implicits._

          val s = b.add(
            new ClientStage[Context, Cmd, Evt](
              connectionsPerHost,
              maximumFailuresPerHost,
              recoveryPeriod,
              true,
              processor,
              protocol
          ))

          reconnectLogic(b, b.add(hosts), s.in2, s.out2)
          source.out ~> s.in1
          s.out1 ~> b.add(eventHandler)

          ClosedShape
    })

  val input = g.run()

  override protected def send(command: Command[Cmd]): Future[Event[Evt]] = {
    val context = Promise[Event[Evt]]()
    input.offer((command, context)).flatMap {
      case QueueOfferResult.Dropped         ⇒ Future.failed(InputQueueUnavailable())
      case QueueOfferResult.QueueClosed     ⇒ Future.failed(InputQueueClosed())
      case QueueOfferResult.Failure(reason) ⇒ Future.failed(reason)
      case QueueOfferResult.Enqueued        ⇒ context.future
    }
  }

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

trait ReactiveTCP[Cmd, Evt] extends Actor with ActorLogging
{
  val uuid: UUID            = UUID.randomUUID()
  val pool: ExecutorService = java.util.concurrent.Executors.newFixedThreadPool(2)

  override def receive: Receive = {
    case frame: CameraFrame => pool.execute {
      () => ask(FrameSeqMessage(uuid, Seq(convertToSocketFrame(frame))))
      //TODO: you can try to process reply
    }
    case frames: Seq[CameraFrame] => pool.execute {
      () => ask(FrameSeqMessage(uuid, frames.map(convertToSocketFrame)))
    }
    case msg => log.warning(s"ReactiveTCPCommunicationActor cannot parse incoming request: $msg!")
  }

  protected def ask(command: MessageFormat)
  : Future[Evt] =
  {
    send(SingularCommand(command.asInstanceOf[Cmd])).flatMap {
      case SingularEvent(x)      => Future(x)(context.dispatcher)
      case SingularErrorEvent(x) => Future.failed(EventException(x))
      case x                     => Future.failed(IncorrectEventType(x))
    }(context.dispatcher)
  }

  protected def send(command: Command[Cmd]): Future[Event[Evt]]

  private def convertToSocketFrame: CameraFrame => SocketFrame = SocketFrameConverter.convertToSocketFrame(_)
}