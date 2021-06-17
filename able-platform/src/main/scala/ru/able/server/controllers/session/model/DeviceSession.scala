package ru.able.services.session.model

import java.util.UUID

import akka.Done
import akka.stream.UniqueKillSwitch
import akka.stream.scaladsl.{RunnableGraph, Tcp}

import scala.concurrent.Future

object SessionController {

  case class SessionID(uuid: UUID)

  sealed trait BaseDeviceState
  case object Foundation extends BaseDeviceState
  case object Definition extends BaseDeviceState
  case object Initialization extends BaseDeviceState
  case object Provision extends BaseDeviceState
  case object Exploitation extends BaseDeviceState

  trait BaseSession {
    def state: BaseDeviceState
    def deviceID: UUID
  }

  case class DeviceSession(state: BaseDeviceState,
                           deviceID: UUID) extends BaseSession

  sealed trait TwinControllerRequest
  case class RegisterNewDeviceTwin(conn: Tcp.IncomingConnection) extends TwinControllerRequest
  case class ResetDeviceTwin(conn: Tcp.IncomingConnection) extends TwinControllerRequest

  sealed trait TwinControllerResponse
  case class DeviceTwinCreated(graph: RunnableGraph[(UniqueKillSwitch, Future[Done])]) extends TwinControllerResponse
  case object DeviceTwinUpdated extends TwinControllerResponse

  sealed trait SessionState
  case class SessionUpdated(sessionID: SessionID) extends SessionState
  case class SessionNotChanged(sessionID: SessionID) extends SessionState
  case object SessionNotFound extends SessionState
}