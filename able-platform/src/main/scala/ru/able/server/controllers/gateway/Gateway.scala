package ru.able.server.controllers.gateway

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{Flow, Keep, Tcp}
import akka.util.{ByteString, Timeout}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}
import ru.able.server.controllers.flow.FlowFactory
import ru.able.server.controllers.flow.model.FlowTypes.{BasicFT, CustomReplyFT, FlowType}
import ru.able.server.controllers.flow.protocol.Action
import ru.able.server.controllers.gateway.model.GatewayModel.{ActiveGW, DeleteGW, GatewayObj, GatewayRouted, RunBasicGateway, RunCustomGateway, UpgradeGW, UpgradeGateway}
import ru.able.server.controllers.session.model.KeeperModel.{ResetConnection, SessionID}
import ru.able.util.Helpers

object Gateway {
  def getReference(implicit system: ActorSystem, ec: ExecutionContext): Future[ActorRef] =
    system
      .actorSelection("akka://ServerActorSystem/user/GatewayActor")
      .resolveOne()(Timeout(Duration(1, TimeUnit.SECONDS)))

  def apply(sessionKeeperActor: ActorRef)(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props(new Gateway(sessionKeeperActor)), "GatewayActor")
}

final class Gateway(_sessionKeeperActor: ActorRef) extends Actor with ActorLogging {
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher

  private val _gateways = new mutable.HashMap[SessionID, GatewayObj]()
  private val _flowFactory = new FlowFactory

  override def receive: Receive = {
    case RunBasicGateway(sessionID, connection) => runBasicGateway(sessionID, connection)
    case RunCustomGateway(sessionID, connection, replyProcessor) => runCustomGateway(sessionID, connection, replyProcessor, sender())
    case UpgradeGateway(sessionID, flowType) => upgradeGateway(sessionID, flowType)
    case _ => log.warning(s"GatewayActor cannot parse incoming request.")
  }

  private def removeGateway(sessionID: SessionID): Unit = {
    _gateways.remove(sessionID) match {
      case Some(gatewayObj) => _sessionKeeperActor ! ResetConnection(gatewayObj.connection.remoteAddress)
      case None => log.warning(s"Remove gateway command received, but gateway with SessionID: ${sessionID.uuid} not found!")
    }
  }

  private def runBasicGateway(sessionID: SessionID, connection: Tcp.IncomingConnection): Unit = {
    val (publisher, workFlow) = _flowFactory.flow(BasicFT)()
    val killSwitch = runGateway(sessionID, connection, workFlow)

    _gateways.update(sessionID, GatewayObj(connection, publisher, killSwitch))
  }

  private def runCustomGateway(sessionID: SessionID,
                               connection: Tcp.IncomingConnection,
                               replyProcessor: AnyRef => Action,
                               requester: ActorRef)
  : Unit = {
    val (publisher, workFlow) = _flowFactory.flow(CustomReplyFT)(replyProcessor)
    val killSwitch = runGateway(sessionID, connection, workFlow)

    _gateways.update(sessionID, GatewayObj(connection, publisher, killSwitch))

    requester ! GatewayRouted(publisher)
  }

  private def runGateway(sessionID: SessionID,
                         connection: Tcp.IncomingConnection,
                         workFlow: Flow[ByteString, ByteString, Future[Done]])
  : UniqueKillSwitch = {
    val (killSwitch, future) = connection
      .flow
      .viaMat(KillSwitches.single)(Keep.right)
      .joinMat(workFlow)(Keep.both)
      .run()

    future.onComplete {
      case Success(msg) => {
        log.info(s"Stream for resolving host: ${connection.remoteAddress} completed with msg: $msg")
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

  private def upgradeGateway(sessionID: SessionID, flowType: FlowType): Unit = {
    _gateways.get(sessionID) match {
      case Some(gatewayObj) => {
//        gatewayObj.killSwitch.shutdown()

//        Helpers.runAfterDelay(10000) {
//          val (publisher, workFlow) = _flowFactory.flow(flowType)()
//          val killSwitch = runGateway(sessionID, gatewayObj.connection, workFlow)
//          _gateways.update(sessionID, GatewayObj(gatewayObj.connection, publisher, killSwitch))
//        }
      }
      case None => log.warning(s"Upgrade gateway command received, but gateway with SessionID: ${sessionID.uuid} not found!")
    }
  }
}