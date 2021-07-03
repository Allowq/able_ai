package ru.able.client

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{ClosedShape, FlowShape, Inlet, Materializer, Outlet, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{BidiFlow, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try
import java.util.UUID

import ru.able.camera.camera.CameraFrame
import ru.able.camera.utils.settings.Settings
import ru.able.client.NetworkClient._
import ru.able.client.pipeline.ClientStage.{HostEvent, HostUp}
import ru.able.client.pipeline.{ClientStage, Host, Processor, Resolver}
import ru.able.client.protocol.{Command, Event, FrameSeqMessage, SimpleCommand, SimpleMessage, SingularCommand, SingularErrorEvent, SingularEvent, StreamEvent, StreamingCommand}
import ru.able.communication.SocketFrameConverter

object NetworkClient {

  val ProviderName                = "NetworkClient"
  private val _reconnectDuration  = Duration(2, TimeUnit.SECONDS)
  private val _shouldReconnect    = true

  private def reconnectLogic[M](builder: GraphDSL.Builder[M],
                                hostEventSource: Source[HostEvent, NotUsed]#Shape,
                                hostEventIn: Inlet[HostEvent],
                                hostEventOut: Outlet[HostEvent])
                               (implicit system: ActorSystem) =
  {
    import GraphDSL.Implicits._
    implicit val b = builder

    val delay = _reconnectDuration
    val groupDelay = Flow[HostEvent]
      .groupBy[Host](1024, { x: HostEvent ⇒ x.host })
      .delay(delay)
      .map { x ⇒ system.log.warning(s"Reconnecting after ${delay.toSeconds}s for ${x.host}"); HostUp(x.host) }
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

  def apply[Cmd, Evt](hosts: Source[HostEvent, NotUsed],
                      settings: Settings,
                      resolver: Resolver[Evt],
                      shouldReact: Boolean,
                      inputOverflowStrategy: OverflowStrategy,
                      protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any])
                     (implicit system: ActorSystem, ec: ExecutionContext): NetworkClient[Cmd, Evt] =
  {
    val processor = Processor[Cmd, Evt](resolver, settings.getProducerParallelism)
    new NetworkClient(
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
                     (implicit system: ActorSystem, ec: ExecutionContext): NetworkClient[Cmd, Evt] =
  {
    val processor = Processor[Cmd, Evt](resolver, settings.getProducerParallelism)
    new NetworkClient(
      Source(hosts.map(HostUp)),
      settings.getMaxConnectionsPerHost,
      settings.getMaxFailuresPerHost,
      settings.getFailureRecoveryPeriod,
      settings.getInputBufferSize,
      inputOverflowStrategy,
      processor,
      protocol.reversed)(system, ec)
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
                    (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) =
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

  def rawFlow[Context, Cmd, Evt](hosts: Source[HostEvent, NotUsed],
                                 maxConnectionPerHost: Int,
                                 maxFailuresPerHost: Int,
                                 failureRecoveryPeriod: FiniteDuration,
                                 producerParallelism: Int,
                                 resolver: Resolver[Evt],
                                 shouldReact: Boolean = false,
                                 protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any])
                                (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) =
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

  def props[Cmd, Evt](settings: Settings,
                      resolver: Resolver[Evt],
                      protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any])
                     (implicit system: ActorSystem, ec: ExecutionContext) = {
    Props(
      new NetworkClient[Cmd, Evt](
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

  trait ClientException

  case class InputQueueClosed() extends Exception with ClientException
  case class InputQueueUnavailable() extends Exception with ClientException
  case class IncorrectEventType[A](event: A) extends Exception with ClientException
  case class EventException[A](cause: A) extends Throwable
}

class NetworkClient[Cmd, Evt](hosts: Source[HostEvent, NotUsed],
                              connectionsPerHost: Int,
                              maximumFailuresPerHost: Int,
                              recoveryPeriod: FiniteDuration,
                              inputBufferSize: Int,
                              inputOverflowStrategy: OverflowStrategy,
                              processor: Processor[Cmd, Evt],
                              protocol: BidiFlow[ByteString, Evt, Cmd, ByteString, Any])
                             (implicit system: ActorSystem, ec: ExecutionContext) extends Actor with ActorLogging
{
  type Context = Promise[Event[Evt]]

  private val _uuid                  = UUID.randomUUID()
  private val _socketFraneConverter  = new SocketFrameConverter()
  private val _pool                  = java.util.concurrent.Executors.newFixedThreadPool(2)

  val eventHandler = Sink.foreach[(Try[Event[Evt]], Promise[Event[Evt]])] {
    case (evt, context) ⇒ context.complete(evt)
  }

  val g = RunnableGraph.fromGraph(
    GraphDSL.create(Source.queue[(Command[Cmd], Promise[Event[Evt]])](inputBufferSize, inputOverflowStrategy)) {
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

  private def send(command: Command[Cmd])(implicit ec: ExecutionContext): Future[Event[Evt]] = {
    val context = Promise[Event[Evt]]()
    input.offer((command, context)).flatMap {
      case QueueOfferResult.Dropped         ⇒ Future.failed(InputQueueUnavailable())
      case QueueOfferResult.QueueClosed     ⇒ Future.failed(InputQueueClosed())
      case QueueOfferResult.Failure(reason) ⇒ Future.failed(reason)
      case QueueOfferResult.Enqueued        ⇒ context.future
    }
  }

  def ask(command: Cmd)(implicit ec: ExecutionContext): Future[Evt] = send(SingularCommand(command)) flatMap {
    case SingularEvent(x)      ⇒ Future(x)
    case SingularErrorEvent(x) ⇒ Future.failed(EventException(x))
    case x                     ⇒ Future.failed(IncorrectEventType(x))
  }

  def askStream(command: Cmd)(implicit ec: ExecutionContext): Future[Source[Evt, Any]] = send(SingularCommand(command)) flatMap {
    case StreamEvent(x)        ⇒ Future(x)
    case SingularErrorEvent(x) ⇒ Future.failed(EventException(x))
    case x                     ⇒ Future.failed(IncorrectEventType(x))
  }

  def sendStream(stream: Source[Cmd, Any])(implicit ec: ExecutionContext): Future[Evt] = send(StreamingCommand(stream)) flatMap {
    case SingularEvent(x)      ⇒ Future(x)
    case SingularErrorEvent(x) ⇒ Future.failed(EventException(x))
    case x                     ⇒ Future.failed(IncorrectEventType(x))
  }

  def sendStream(command: Cmd, stream: Source[Cmd, Any])(implicit ec: ExecutionContext): Future[Evt] = send(StreamingCommand(Source.single(command) ++ stream)) flatMap {
    case SingularEvent(x)      ⇒ Future(x)
    case SingularErrorEvent(x) ⇒ Future.failed(EventException(x))
    case x                     ⇒ Future.failed(IncorrectEventType(x))
  }

  def react(stream: Source[Cmd, Any])(implicit ec: ExecutionContext): Future[Source[Evt, Any]] = send(StreamingCommand(stream)) flatMap {
    case StreamEvent(x)        ⇒ Future(x)
    case SingularErrorEvent(x) ⇒ Future.failed(EventException(x))
    case x                     ⇒ Future.failed(IncorrectEventType(x))
  }

  override def receive: Receive = {
    case frame: CameraFrame => _pool.execute {
      () => ask(FrameSeqMessage(_uuid, Seq(_socketFraneConverter.convert(frame))).asInstanceOf[Cmd])
      //TODO: you can try to process reply
    }
    case frames: Seq[CameraFrame] => _pool.execute {
      () => ask( FrameSeqMessage(_uuid, frames.map(_socketFraneConverter.convert)).asInstanceOf[Cmd] )
    }
  }
}