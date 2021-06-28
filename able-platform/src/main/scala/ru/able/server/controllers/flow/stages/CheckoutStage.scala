package ru.able.server.controllers.flow.stages

import java.net.InetSocketAddress
import java.util.{Timer, TimerTask, UUID}
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import ru.able.server.controllers.flow.model.SimpleReply
import ru.able.server.controllers.session.model.KeeperModel.{ActiveSession, CheckSession, CheckSessionState, DeviceID, ExpiredSession, InitSession, ResolveDeviceID, SessionState}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

class CheckoutStage[Evt](rAddr: InetSocketAddress, _sessionKeeperActor: ActorRef)
                        (implicit ec: ExecutionContext) extends GraphStage[FlowShape[Evt, Evt]] with LazyLogging
{
  implicit val askTimeout = Timeout(Duration(1, TimeUnit.SECONDS))

  private var _sessionState: SessionState = InitSession
  private val _timer = new Timer

  private val eventIn = Inlet[Evt]("CheckoutStage.Event.In")
  private val eventOut = Outlet[Evt]("CheckoutStage.Event.Out")

  val shape = new FlowShape(eventIn, eventOut)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    override def preStart(): Unit = {
      if (_sessionKeeperActor == Actor.noSender)
        _sessionState = CheckSession
      pull(eventIn)
    }

    setHandler(eventIn, new InHandler {
      override def onPush(): Unit = {
        Try {
          val evt = grab(eventIn)

          _sessionState match {
            case InitSession if eventProcessor(evt) => push(eventOut, evt)
            case InitSession => logger.info(s"Session initialization in progress. Unhandled message: $evt.")
            case CheckSession => logger.info(s"Session checking in progress. Unhandled message: $evt.")
            case ActiveSession => push(eventOut, evt)
            case ExpiredSession => logger.warn(s"Session expired! Unhandled message: $evt.")
          }
        } recover {
          case e: Throwable => logger.error(s"Checkout stage cannot parse incoming message: ${e.getMessage}", e)
        }
        if (_sessionState != ActiveSession) pull(eventIn)
      }
    })

    setHandler(eventOut, new OutHandler {
      override def onPull(): Unit = if (!hasBeenPulled(eventIn)) pull(eventIn)
    })
  }

  private def checkSession(): Unit = {
    Try[SessionState] {
      val askSessionKeeper = (_sessionKeeperActor ? CheckSessionState(rAddr)).mapTo[SessionState]
      Await.result(askSessionKeeper, askTimeout.duration) match {
        case CheckSession => InitSession
        case state => state
      }
    } match {
      case Success(state) => {
        _sessionState = state
        logger.info(s"Checkout stage state update to: $state.")
      }
      case Failure(exception) => {
        logger.warn(s"State updating failure with exception: $exception!")
        _sessionState = InitSession
      }
    }
  }

  private def eventProcessor: PartialFunction[Evt, Boolean] = {
    case SimpleReply(payload) => {
      val uuid = UUID.fromString(payload.asInstanceOf[String])
      _sessionKeeperActor ! ResolveDeviceID(rAddr, DeviceID(Some(uuid)))

      _sessionState = CheckSession
      executeAfterDelay(() => checkSession, askTimeout.duration.toMillis)
      logger.info(s"Session checking in progress. Receive UUID: $uuid.")

      true
    }
    case _ => false
  }

  private def executeAfterDelay(func: () => Unit, delayMills: Long): Unit =
    _timer.schedule(new TimerTask() {
      def run: Unit = func()
    }, delayMills)
}
