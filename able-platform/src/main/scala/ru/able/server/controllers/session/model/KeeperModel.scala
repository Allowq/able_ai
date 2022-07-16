package ru.able.server.controllers.session.model

import java.net.InetSocketAddress
import java.sql.Timestamp
import java.util.UUID

import akka.stream.scaladsl.Tcp

object KeeperModel {

  sealed trait SessionKeeperRequest
  case class NewConnection(conn: Tcp.IncomingConnection) extends SessionKeeperRequest
  case class ResetConnection(rAddr: InetSocketAddress) extends SessionKeeperRequest
  case class ResolveDeviceID(rAddr: InetSocketAddress, deviceID: DeviceID) extends SessionKeeperRequest
  case class CheckSessionState(rAddr: InetSocketAddress) extends SessionKeeperRequest

  case class ResolveConnection(connection: Tcp.IncomingConnection, sessionID: SessionID) extends SessionKeeperRequest

  sealed trait SessionState
  case object InitSession extends SessionState
  case object ActiveSession extends SessionState
  case object ExpiredSession extends SessionState

  case class DeviceID(uuid: Option[UUID])

  trait BaseSession {
    def state: SessionState
    def timestamp: Timestamp
  }
  case class SessionData(deviceID: DeviceID,
                         state: SessionState,
                         timestamp: Timestamp) extends BaseSession

  case class SessionID(uuid: UUID)
  case class SessionObj(id: SessionID, data: SessionData)
}
