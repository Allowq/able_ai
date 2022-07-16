package ru.able.server.controllers.gateway

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{Flow, Keep, Tcp}
import akka.util.{ByteString, Timeout}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import ru.able.server.controllers.flow.FlowFactory
import ru.able.server.controllers.flow.model.FlowTypes.{BasicFT, ExtendedFT}
import ru.able.server.controllers.flow.model.SimpleCommand
import ru.able.server.controllers.flow.protocol.{MessageProtocol, SingularCommand}
import ru.able.server.controllers.flow.stages.CheckoutStage.SetActiveSession
import ru.able.server.controllers.gateway.model.GatewayModel.{ActivateGateway, GatewayObj, RunBasicGateway, RunCustomGateway}
import ru.able.server.controllers.session.model.KeeperModel.{ResetConnection, SessionID}

import scala.concurrent.duration.Duration

object BasicGateway {
  // Example of getReference method
  // https://stackoverflow.com/questions/33398045/what-is-the-best-way-to-get-an-actor-from-the-context-in-akka
  def getReference(implicit system: ActorSystem, ec: ExecutionContext): Future[ActorRef] =
    system
      .actorSelection("akka://ServerActorSystem/user/GatewayActor")
      .resolveOne()(Timeout(Duration(1, TimeUnit.SECONDS)))

  def apply(sessionKeeperActor: ActorRef)(implicit context: ActorContext): ActorRef =
    context.actorOf(Props(new BasicGateway(sessionKeeperActor)), "GatewayActor")
}

private [gateway] class BasicGateway(_sessionKeeperActor: ActorRef) extends Actor with ActorLogging {
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher

  protected val _gateways = new mutable.HashMap[SessionID, GatewayObj]()
  protected val _flowFactory = new FlowFactory

  override def receive: Receive = {
    case RunCustomGateway(sessionID, connection) => runCustomGateway(sessionID, connection)
    case RunBasicGateway(sessionID, connection) => runBasicGateway(sessionID, connection)
    case ActivateGateway(sessionID) => activateGateway(sessionID)
    case msg => log.warning(s"GatewayActor cannot parse incoming request: $msg")
  }

  protected def activateGateway(sessionID: SessionID): Unit = {
    _gateways.get(sessionID) match {
      case Some(gatewayObj) =>
        if (gatewayObj.checkoutHandler != ActorRef.noSender) gatewayObj.checkoutHandler ! SetActiveSession
      case None =>
        log.warning(s"Cannot activate Gateway with ID: ${sessionID.uuid}, because Gateway not found!")
    }
  }

  protected def runBasicGateway(sessionID: SessionID, connection: Tcp.IncomingConnection)
  : Unit =
  {
    val (actionPublisher, workFlow) = _flowFactory.basicFlow
    val killSwitch = runGateway(sessionID, connection, workFlow)

    _gateways.update(sessionID, GatewayObj(connection, BasicFT, ActorRef.noSender, actionPublisher, killSwitch))
  }

  protected def runCustomGateway(sessionID: SessionID, connection: Tcp.IncomingConnection)
  : Unit =
  {
    val (checkoutHandler, commandPublisherActor, workFlow) =
      _flowFactory.detectionManagedFlow(Some(connection.remoteAddress), _sessionKeeperActor)
    val killSwitch = runGateway(sessionID, connection, workFlow)

    _gateways.update(sessionID, GatewayObj(connection, ExtendedFT, checkoutHandler, commandPublisherActor, killSwitch))

    commandPublisherActor ! SingularCommand(SimpleCommand(MessageProtocol.UUID, ""))
  }

  protected def runGateway(sessionID: SessionID,
                         connection: Tcp.IncomingConnection,
                         workFlow: Flow[ByteString, ByteString, Future[Done]])
  : UniqueKillSwitch =
  {
    val (killSwitch, future) = connection
      .flow
      .viaMat(KillSwitches.single)(Keep.right)
      .joinMat(workFlow)(Keep.both)
      .run()

    future.onComplete {
      case Success(msg) => {
        log.info(s"Stream for resolving host: ${connection.remoteAddress} completed with msg: $msg")
        killSwitch.shutdown()
        removeGateway(sessionID)
      }
      case Failure(ex) => {
        log.warning(s"Stream for resolving host: ${connection.remoteAddress} closed with error: ${ex.toString}")
        killSwitch.abort(new Exception("Force stopped from outside!"))
        removeGateway(sessionID)
      }
    }

    killSwitch
  }

  private def removeGateway(sessionID: SessionID): Unit = {
    _gateways.remove(sessionID) match {
      case Some(gatewayObj) => _sessionKeeperActor ! ResetConnection(gatewayObj.connection.remoteAddress)
      case None => log.warning(s"Remove gateway command received, but gateway with SessionID: ${sessionID.uuid} not found!")
    }
  }
}