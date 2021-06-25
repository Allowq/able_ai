package ru.able.server.controllers.session.model

import java.net.InetSocketAddress
import java.sql.Timestamp
import java.util.UUID

import akka.stream.scaladsl.Tcp

object KeeperModel {

  case class NewConnection(conn: Tcp.IncomingConnection)
  case class ResetConnection(rAddr: InetSocketAddress)
  case class ResolveConnection(connection: Tcp.IncomingConnection, sessionID: SessionID)
  case class ResolveDeviceID(connection: Tcp.IncomingConnection, deviceID: DeviceID)

  sealed trait BaseDeviceState
  case object Foundation extends BaseDeviceState
  case object Definition extends BaseDeviceState
  case object Initialization extends BaseDeviceState
  case object Provision extends BaseDeviceState
  case object Exploitation extends BaseDeviceState

  trait BaseSession {
    def state: BaseDeviceState
    def timestamp: Timestamp
  }
  case class SessionData(deviceID: DeviceID,
                         state: BaseDeviceState,
                         timestamp: Timestamp) extends BaseSession

  case class SessionID(uuid: UUID)
  case class SessionObj(id: SessionID, data: SessionData)

  case class DeviceID(uuid: Option[UUID])
}
