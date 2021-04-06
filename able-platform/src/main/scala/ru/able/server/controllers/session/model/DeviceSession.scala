package ru.able.services.session.model

import java.util.UUID

import akka.stream.scaladsl.Tcp

sealed trait BaseDeviceState
case object Foundation extends BaseDeviceState
case object Definition extends BaseDeviceState
case object Initialization extends BaseDeviceState
case object Provision extends BaseDeviceState
case object Exploitation extends BaseDeviceState

trait BaseSession {
  def state: BaseDeviceState
  def deviceUUID: UUID
  def source: Tcp.IncomingConnection
}

case class DeviceSession(state: BaseDeviceState,
                         deviceUUID: UUID,
                         source: Tcp.IncomingConnection) extends BaseSession

sealed trait SessionRequest
case class CheckSessionExist(conn: Tcp.IncomingConnection) extends SessionRequest
case class ResetSession(conn: Tcp.IncomingConnection) extends SessionRequest

sealed trait SessionResponse
case object SessionUpdated extends SessionResponse
case object SessionNotFound extends SessionResponse