package ru.able.server.controllers.gateway.model

import akka.actor.ActorRef
import akka.stream.UniqueKillSwitch
import akka.stream.scaladsl.Tcp
import ru.able.server.controllers.flow.model.FlowTypes.FlowType
import ru.able.server.controllers.session.model.KeeperModel.SessionID

object GatewayModel {

  sealed trait GatewayRequest
  case class RunBasicGateway(sessionID: SessionID, connection: Tcp.IncomingConnection) extends GatewayRequest
  case class RunCustomGateway(sessionID: SessionID, connection: Tcp.IncomingConnection) extends GatewayRequest

  sealed trait GatewayResponse
  case class GatewayRouted(publisher: ActorRef) extends GatewayResponse

  case class GatewayObj(connection: Tcp.IncomingConnection,
                        flowType: FlowType,
                        publisher: ActorRef,
                        killSwitch: UniqueKillSwitch)
}
