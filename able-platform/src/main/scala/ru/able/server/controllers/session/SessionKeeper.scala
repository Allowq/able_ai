package ru.able.services.session

import java.net.InetSocketAddress
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.Tcp
import akka.util.Timeout
import akka.pattern.ask

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import ru.able.server.controllers.gateway.Gateway
import ru.able.server.controllers.session.ConnectionResolver
import ru.able.server.controllers.session.model.KeeperModel.{ActiveSession, CheckSessionState, DeviceID, ExpiredSession, InitSession, NewConnection, ResetConnection, ResolveConnection, ResolveDeviceID, SessionData, SessionID, SessionObj}

object SessionKeeper {
  def apply()(implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(Props(new SessionKeeper()), "SessionKeeperActor")

  def getReference(implicit system: ActorSystem, ec: ExecutionContext): Future[ActorRef] =
    system
      .actorSelection("akka://ServerActorSystem/user/SessionKeeperActor")
      .resolveOne()(Timeout(Duration(1, TimeUnit.SECONDS)))
}

final class SessionKeeper private extends Actor with ActorLogging {
  // TODO: Define FSM for suspending status on DeviseController initialize timeout
  implicit val askTimeout = Timeout(Duration(5, TimeUnit.SECONDS))
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher

  private val _sessionConnections = new mutable.HashMap[InetSocketAddress, SessionObj]
  private val _gatewayActor = Gateway(self)

  private lazy val _connectionResolver = ConnectionResolver.createActorPool(self, _gatewayActor)

  override def receive: Receive = {
    case NewConnection(conn)        => processNewConnection(conn)
    case ResetConnection(rAddr)     => resetConnection(rAddr)
    case ResolveDeviceID(conn, id)  => updateDeviceID(conn, id)
    case CheckSessionState(rAddr)   => checkSessionState(rAddr, sender())
    case msg                        => log.warning(s"SessionKeeper received unrecognized message: $msg")
  }

  private def createNewSession(conn: Tcp.IncomingConnection): SessionID = {
    val sessionID = SessionID(UUID.randomUUID())

    _sessionConnections.update(
      conn.remoteAddress,
      SessionObj(sessionID, SessionData(DeviceID(None), InitSession, Timestamp.from(Instant.now())))
    )
    sessionID
  }

  private def checkSessionState(address: InetSocketAddress, requester: ActorRef): Unit = {
    _sessionConnections.get(address) match {
      case Some(sessionObj) => requester ! sessionObj.data.state
      case None => requester ! ExpiredSession
    }
  }

  private def processNewConnection(conn: Tcp.IncomingConnection): Unit = {
    _sessionConnections.get(conn.remoteAddress) match {
      case Some(session) => {
        log.warning(s"Connection from host: ${conn.remoteAddress} has been established before! Check session with ID: ${session.id}")
        updateSession(conn, session)
      }
      case None => {
        val newSessionID: SessionID = createNewSession(conn)
        log.info(s"Connection with host: ${conn.remoteAddress} established. New session with ID: $newSessionID registered.")
        resolveDevice(conn, newSessionID)
      }
    }
  }

  private def resetConnection(address: InetSocketAddress): Unit = {
    _sessionConnections.get(address) match {
      case Some(session) => {
        _sessionConnections.update(
          address,
          session.copy(data = session.data.copy(DeviceID(None), ExpiredSession, Timestamp.from(Instant.now())))
        )
        log.info(s"Host with: ${address} disconnected. Session with ID: ${session.id} reseted.")
      }
      case None => log.warning(s"Command with resetConnection signal received, but connection with address: $address not found.")
    }
  }

  private def resolveDevice(connection: Tcp.IncomingConnection, sessionID: SessionID): Unit = {
    _connectionResolver ! ResolveConnection(connection, sessionID)

    context.system.scheduler.scheduleOnce(askTimeout.duration) {
      _sessionConnections.get(connection.remoteAddress) match {
        case Some(sessionObj) => if (sessionObj.data.deviceID.uuid.isEmpty) {
          log.info(s"Cannot resolve host: ${connection.remoteAddress}. Session with ID: $sessionID will be remove.")
          resetConnection(connection.remoteAddress)
        }
        case _ => log.warning(s"Session was removed during host (address: ${connection.remoteAddress}) resolving.")
      }
    }
  }

  private def updateDeviceID(rAddr: InetSocketAddress, id: DeviceID): Unit = {
    val sessionOpt: Option[SessionObj] = _sessionConnections.get(rAddr)

    (sessionOpt, id.uuid) match {
      case (Some(session), Some(_)) => {
        _sessionConnections.update(
          rAddr,
          session.copy(data = session.data.copy(
            state = ActiveSession,
            deviceID = id,
            timestamp = Timestamp.from(Instant.now())
          ))
        )
        log.info(s"Resolving host: $rAddr done. DeviceID is: $id.")
      }
      case (None, Some(id)) =>
        log.warning(s"Host: $rAddr resolved with DeviceID: $id, but session not found!")
      case (_, None) =>
        log.warning(s"Received DeviceID update request without UUID!")
    }
  }

  private def updateSession(conn: Tcp.IncomingConnection, session: SessionObj): Unit = {
    _sessionConnections.update(
      conn.remoteAddress,
      session.copy(data = session.data.copy(timestamp = Timestamp.from(Instant.now())))
    )
  }
}