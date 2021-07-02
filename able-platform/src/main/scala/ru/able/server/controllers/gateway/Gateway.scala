package ru.able.server.controllers.gateway

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{Flow, Keep, Tcp}
import akka.util.{ByteString, Timeout}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}
import ru.able.server.controllers.flow.FlowFactory
import ru.able.server.controllers.flow.model.FlowTypes.{BasicFT, ExtendedFT}
import ru.able.server.controllers.gateway.model.GatewayModel.{GatewayObj, GatewayRouted, RunBasicGateway, RunCustomGateway}
import ru.able.server.controllers.session.model.KeeperModel.{ResetConnection, SessionID}

object Gateway {
  def getReference(implicit system: ActorSystem, ec: ExecutionContext): Future[ActorRef] =
    system
      .actorSelection("akka://ServerActorSystem/user/GatewayActor")
      .resolveOne()(Timeout(Duration(1, TimeUnit.SECONDS)))

  def apply(sessionKeeperActor: ActorRef)(implicit context: ActorContext): ActorRef =
    context.actorOf(Props(new Gateway(sessionKeeperActor)), "GatewayActor")
}

final class Gateway(_sessionKeeperActor: ActorRef) extends Actor with ActorLogging {
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher

  private val _gateways = new mutable.HashMap[SessionID, GatewayObj]()
  private val _flowFactory = new FlowFactory

  override def receive: Receive = {
    case RunBasicGateway(sessionID, connection) => runBasicGateway(sessionID, connection)
    case RunCustomGateway(sessionID, connection) => runCustomGateway(sessionID, connection, sender())
    case _ => log.warning(s"GatewayActor cannot parse incoming request.")
  }

  private def removeGateway(sessionID: SessionID): Unit = {
    _gateways.remove(sessionID) match {
      case Some(gatewayObj) => _sessionKeeperActor ! ResetConnection(gatewayObj.connection.remoteAddress)
      case None => log.warning(s"Remove gateway command received, but gateway with SessionID: ${sessionID.uuid} not found!")
    }
  }

  private def runBasicGateway(sessionID: SessionID, connection: Tcp.IncomingConnection): Unit = {
    val (actionPublisher, workFlow) = _flowFactory.flow(BasicFT)(None)
    val killSwitch = runGateway(sessionID, connection, workFlow)

    _gateways.update(sessionID, GatewayObj(connection, BasicFT, actionPublisher, killSwitch))
  }

  private def runCustomGateway(sessionID: SessionID,
                               connection: Tcp.IncomingConnection,
                               requester: ActorRef)
  : Unit =
  {
    val (actionPublisher, workFlow) = _flowFactory.flow(ExtendedFT)(Some(connection.remoteAddress), _sessionKeeperActor)
    val killSwitch = runGateway(sessionID, connection, workFlow)

    _gateways.update(sessionID, GatewayObj(connection, ExtendedFT, actionPublisher, killSwitch))

    requester ! GatewayRouted(actionPublisher)
  }

  private def runGateway(sessionID: SessionID,
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
}