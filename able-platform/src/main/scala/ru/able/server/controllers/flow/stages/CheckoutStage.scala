package ru.able.server.controllers.flow.stages

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.{Timer, UUID}

import akka.actor.{Actor, ActorContext, ActorRef}
import akka.pattern.ask
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import ru.able.util.Helpers.runAfterDelay
import ru.able.server.controllers.flow.CommandActorPublisher
import ru.able.server.controllers.flow.CommandActorPublisher.AssignStageActor
import ru.able.server.controllers.flow.model.SimpleReply
import ru.able.server.controllers.flow.protocol.Command
import ru.able.server.controllers.session.model.KeeperModel
import ru.able.server.controllers.session.model.KeeperModel.{ActiveSession, CheckSession, CheckSessionState, DeviceID, ExpiredSession, InitSession, ResolveDeviceID, SessionState}

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object CheckoutStage {

  def apply[Evt, Cmd](rAddrOpt: Option[InetSocketAddress], sessionKeeper: ActorRef = Actor.noSender)
                     (implicit context: ActorContext)
  : Tuple2[ActorRef, CheckoutStage[Evt, Cmd]] =
  {
    val stageControlActor = rAddrOpt match {
      case Some(rAddr) => {
        val actorName = rAddr.toString.replaceAll("[/]", "_")
        context.actorOf(CommandActorPublisher.props, s"CheckoutStageActor$actorName")
      }
      case None => context.actorOf(CommandActorPublisher.props)
    }
    (stageControlActor, new CheckoutStage(stageControlActor)(rAddrOpt, sessionKeeper))
  }
}

class CheckoutStage[Evt, Cmd] private (_stageControlActor: ActorRef)
                                      (_rAddr: Option[InetSocketAddress], _sessionKeeper: ActorRef)
  extends GraphStage[FlowShape[Evt, Evt]] with LazyLogging
{
  implicit val askTimeout = Timeout(Duration(1, TimeUnit.SECONDS))

  private var _sessionState: SessionState = InitSession
  private val _timer = new Timer

  private val eventIn = Inlet[Evt]("CheckoutStage.Event.In")
  private val eventOut = Outlet[Evt]("CheckoutStage.Event.Out")

  val shape = new FlowShape(eventIn, eventOut)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private lazy val self: StageActor = getStageActor(onMessage)
    private var _messages: Queue[Command[Cmd]] = Queue()

    override def preStart(): Unit = {
      _stageControlActor ! AssignStageActor(self.ref)

      if (_sessionKeeper == Actor.noSender || _rAddr.isEmpty)
        _sessionState = ExpiredSession
    }

    setHandler(eventIn, new InHandler {
      override def onPush(): Unit = {
        val evt = grab(eventIn)

        _sessionState match {
          case ActiveSession                          => push(eventOut, evt)
          case s =>
            s match {
              case ExpiredSession => logger.warn(s"Session expired! Unhandled message: $evt.")
              case CheckSession   => logger.info(s"Session checking in progress. Unhandled message: $evt.")
              case InitSession if checkReplyUUID(evt) => ()
              case InitSession    => logger.info(s"Session initialization in progress. Unhandled message: $evt.")
              case ex             => logger.warn(s"CheckoutStage cannot parse incoming message: $ex")
            }
            pull(eventIn)
        }
      }
    })

    setHandler(eventOut, new OutHandler { override def onPull(): Unit = if (!hasBeenPulled(eventIn)) pull(eventIn) })

    private def onMessage(x: (ActorRef, Any)): Unit = {
      x match {
        case (_, msg: Command[Cmd]) => {
          _messages = _messages.enqueue(msg)
          pump()
        }
        // TODO: Repair it
        case (sender, msg) => logger.warn(s"Cannot process message: $msg from $sender!")
      }
    }

    private def pump(): Unit = {
      if (_messages.nonEmpty) {
        _messages.dequeue match {
          case (msg: Command[Cmd], newQueue: Queue[Cmd]) => msg match {
            case _ => _messages = newQueue
          }
        }
      }
    }

    private def checkReplyUUID: PartialFunction[Evt, Boolean] = {
      case SimpleReply(payload) if _rAddr.isDefined => {
        val uuid = UUID.fromString(payload.asInstanceOf[String])
        _sessionKeeper ! ResolveDeviceID(_rAddr.get, DeviceID(Some(uuid)))
        _sessionState = CheckSession
        runAfterDelay(askTimeout.duration.toMillis, _timer)(() => requestStateFromKeeper)

        true
      }
      case _ => false
    }

    private def requestStateFromKeeper(): Unit =
      (_sessionKeeper ? CheckSessionState(_rAddr.get)).mapTo[SessionState].onComplete {
        case Success(value) => value match {
          case KeeperModel.CheckSession         => _sessionState = InitSession
          case state: KeeperModel.SessionState  => _sessionState = state
        }
        case Failure(ex) => {
          _sessionState = InitSession
          logger.warn(s"State updating failure with exception: $ex!")
        }
      }(materializer.executionContext)
  }
}
