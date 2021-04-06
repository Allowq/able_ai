package ru.able.services.session

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.scaladsl.Tcp
import ru.able.services.session.model.{BaseDeviceState, CheckSessionExist, DeviceSession, Foundation, ResetSession, SessionNotFound, SessionResponse, SessionUpdated}

import scala.collection.mutable

object SessionController {
  def props = Props(new SessionController())
}

class SessionController extends Actor with ActorLogging {
  private val _connectionMap = new mutable.HashMap[InetSocketAddress, DeviceSession]

  override def receive: Receive = {
    case CheckSessionExist(conn) => {
      _connectionMap.get(conn.remoteAddress) match {
        case Some(DeviceSession(state, _, _)) => sender() ! processDeviceState(state, conn)
        case None => sender() ! processDeviceState(Foundation, conn)
      }
    }
    case ResetSession(conn) => {
      _connectionMap.get(conn.remoteAddress) match {
        case Some(DeviceSession(_, u, c)) => {
          _connectionMap.update(conn.remoteAddress, DeviceSession(Foundation, u, c))
          sender() ! SessionUpdated
        }
        case None => sender() ! SessionNotFound
      }
    }
  }

  private def processDeviceState(state: BaseDeviceState, conn: Tcp.IncomingConnection): SessionResponse = state match {
    case Foundation => {
      _connectionMap.update(conn.remoteAddress, DeviceSession(state, UUID.randomUUID, conn))
      SessionUpdated
    }
//    case Definition =>
//    case Initialization =>
//    case Provision =>
//    case Exploitation =>
  }
}
