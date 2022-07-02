package ru.able.communication.viatcp.stage.bridge

import java.util.UUID
import java.util.concurrent.ExecutorService

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{ClosedShape, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{BidiFlow, GraphDSL, RunnableGraph, Sink, Source}
import akka.util.ByteString
import ru.able.camera.framereader.model.CameraFrame
import ru.able.camera.utils.settings.Settings
import ru.able.communication.viasocket.{SocketFrame, SocketFrameConverter}
import ru.able.communication.viatcp.EventBus
import ru.able.communication.viatcp.protocol.{Command, Event, FrameSeqMessage, MessageFormat, MessageProtocol, SimpleCommand, SimpleReply, SingularCommand, SingularErrorEvent, SingularEvent, SubscribeOnEvents, UnsubscribeFromEvents}
import ru.able.communication.viatcp.stage.ClientStage.HostUp
import ru.able.communication.viatcp.stage.bridge.BridgeBase.{EventException, IncorrectEventType, InputQueueClosed, InputQueueUnavailable}
import ru.able.communication.viatcp.stage.{ClientStage, Host, Processor, Resolver}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

object ReactiveBridge {
  val ProviderName = "ReactiveBridge"

  def props[Cmd, Evt](settings: Settings,
                      resolver: Resolver[Evt],
                      protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any],
                      eventBus: EventBus)
                     (implicit system: ActorSystem, ec: ExecutionContext) =
  {
    Props(new ReactiveBridge[Cmd, Evt](settings, resolver, protocol, eventBus))
  }
}

class ReactiveBridge[Cmd, Evt](settings: Settings,
                               resolver: Resolver[Evt],
                               protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any],
                               eventBus: EventBus)
                              (implicit system: ActorSystem, ec: ExecutionContext)
  extends BridgeBase[Cmd, Evt] with Actor with ActorLogging
{
  protected val clientUUID: UUID = settings.clientUUID
  protected val eventBusOpt: Option[EventBus] = Some(eventBus)
  protected val pool: ExecutorService = java.util.concurrent.Executors.newFixedThreadPool(2)

  override val eventHandler = Sink.foreach[(Try[Event[Evt]], Context)] {
    case (Failure(msg), context) => context.failure(msg)
    case (Success(evt), context) => {
      evt match {
        case SingularEvent(SimpleCommand(MessageProtocol.UUID, _)) =>
          ask(SimpleReply(clientUUID.toString))
        case _ =>
          eventBusOpt.map(_.publish[Evt](evt))
      }
      context.success(evt)
    }
  }

  override val g = RunnableGraph.fromGraph(
    GraphDSL.create(Source.queue[(Command[Cmd], Context)](settings.inputBufferSize, OverflowStrategy.backpressure)) {
      implicit b =>
        source =>
          import GraphDSL.Implicits._

          val s = b.add(
            new ClientStage[Context, Cmd, Evt](
              settings.maxConnectionsPerHost,
              settings.maxFailuresPerHost,
              settings.failureRecoveryPeriod,
              true,
              Processor[Cmd, Evt](resolver, settings.clientParallelismValue)(ec),
              protocol.reversed
            ))

          BridgeBase.reconnectLogic(
            b,
            b.add(Source.single(HostUp(Host(settings.networkClientHostname, settings.networkClientPort)))),
            s.in2,
            s.out2)

          source.out ~> s.in1
          s.out1 ~> b.add(eventHandler)

          ClosedShape
    })

  override val input = g.run()

  override def receive: Receive = {
    case frame: CameraFrame => pool.execute {
      () => ask(FrameSeqMessage(clientUUID, Seq(convertToSocketFrame(frame))))
    }
    case frames: Seq[CameraFrame] => pool.execute {
      () => ask(FrameSeqMessage(clientUUID, frames.map(convertToSocketFrame)))
    }
    case SubscribeOnEvents(subscriber, event) => eventBusOpt.map(_.subscribe(subscriber, event))
    case UnsubscribeFromEvents(subscriber)    => eventBusOpt.map(_.unsubscribe(subscriber))

    case msg => log.warning(s"ReactiveBridgeActor (via TCP) cannot parse incoming request: $msg!")
  }

  protected def ask(command: MessageFormat): Future[Evt] =
  {
    send(SingularCommand(command.asInstanceOf[Cmd])).flatMap {
      case SingularEvent(x)      => Future(x)(context.dispatcher)
      case SingularErrorEvent(x) => Future.failed(EventException(x))
      case x                     => Future.failed(IncorrectEventType(x))
    }(context.dispatcher)
  }

  protected def convertToSocketFrame: CameraFrame => SocketFrame = SocketFrameConverter.convertToSocketFrame(_)

  override protected def send(command: Command[Cmd]): Future[Event[Evt]] = {
    val context = Promise[Event[Evt]]()
    input.offer((command, context)).flatMap {
      case QueueOfferResult.Dropped         ⇒ Future.failed(InputQueueUnavailable())
      case QueueOfferResult.QueueClosed     ⇒ Future.failed(InputQueueClosed())
      case QueueOfferResult.Failure(reason) ⇒ Future.failed(reason)
      case QueueOfferResult.Enqueued        ⇒ context.future
    }
  }
}