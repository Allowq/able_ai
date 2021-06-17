package ru.able.services.session

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp
import com.typesafe.scalalogging.LazyLogging
import ru.able.services.session.model.SessionController.{DeviceSession, SessionState, Foundation, SessionID, SessionNotFound, SessionUpdated}

import scala.collection.mutable

object SessionController {
  private var _instance: Option[SessionController] = None

  def apply()(implicit system: ActorSystem): SessionController =
    _instance.getOrElse{
      _instance = Some(new SessionController)
      _instance.get
    }
}

class SessionController private extends LazyLogging {
  private val _sessionConnections = new mutable.HashMap[InetSocketAddress, SessionID]
  private val _sessionMap = new mutable.HashMap[SessionID, DeviceSession]

  def getOrCreateSession(conn: Tcp.IncomingConnection): SessionID = {
    _sessionConnections.get(conn.remoteAddress) match {
      case Some(sessionID) => _sessionMap.get(sessionID) match {
        case Some(session) =>
          _sessionMap.update(sessionID, DeviceSession(Foundation, session.deviceID))
          sessionID
        case None => {
          logger.warn(s"SessionID: $sessionID has been created, but session not found")
          createSession(conn, Some(sessionID))
        }
      }
      case None => createSession(conn)
    }
  }

  def resetSession(conn: => Tcp.IncomingConnection, sessionIDOpt: Option[SessionID]): SessionState = {
    val idOpt: Option[SessionID] = sessionIDOpt match {
      case Some(_) => sessionIDOpt
      case None => _sessionConnections.get(conn.remoteAddress)
    }

    idOpt match {
      case Some(id) => _sessionMap.get(id) match {
        case Some(session) => _sessionMap.update(id, DeviceSession(Foundation, session.deviceID)); SessionUpdated(id)
        case None => {
          logger.warn(s"SessionID: $id has been reset, but session not found")
          _sessionConnections.remove(conn.remoteAddress)
          SessionNotFound
        }
      }
      case None => SessionNotFound
    }
  }

  private def createSession(conn: Tcp.IncomingConnection, sessionIDOpt: Option[SessionID] = None): SessionID = {
    val uuid = sessionIDOpt match {
      case Some(SessionID(id)) => id
      case None => UUID.randomUUID()
    }

    _sessionMap.update(SessionID(uuid), DeviceSession(Foundation, UUID.randomUUID()))
    _sessionConnections.update(conn.remoteAddress, SessionID(uuid))
    SessionID(uuid)
  }
}
