package ru.able.services.session.model

import akka.Done
import akka.stream.UniqueKillSwitch
import akka.stream.scaladsl.{RunnableGraph, Tcp}

import scala.concurrent.Future

import ru.able.server.controllers.session.model.KeeperModel.SessionID

object SessionController {

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