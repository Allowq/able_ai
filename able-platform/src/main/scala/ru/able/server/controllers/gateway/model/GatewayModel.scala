package ru.able.server.controllers.gateway.model

import java.net.InetSocketAddress

import akka.actor.ActorRef
import akka.stream.UniqueKillSwitch
import akka.stream.scaladsl.Tcp
import ru.able.server.controllers.flow.model.FlowTypes.FlowType
import ru.able.server.controllers.flow.protocol.Action
import ru.able.server.controllers.session.model.KeeperModel.SessionID

object GatewayModel {

  sealed trait GatewayState
  case object ActiveGW extends GatewayState
  case object UpgradeGW extends GatewayState
  case object DeleteGW extends GatewayState

  sealed trait GatewayRequest
  case class RunBasicGateway(sessionID: SessionID, connection: Tcp.IncomingConnection) extends GatewayRequest
  case class RunCustomGateway(sessionID: SessionID,
                              connection: Tcp.IncomingConnection,
                              replyProcessor: AnyRef => Action) extends GatewayRequest
  case class UpgradeGateway(sessionID: SessionID, flowType: FlowType)

  sealed trait GatewayResponse
  case class GatewayRouted(publisher: ActorRef) extends GatewayResponse

  case class GatewayObj(connection: Tcp.IncomingConnection,
                        publisher: ActorRef,
                        killSwitch: UniqueKillSwitch)
}
