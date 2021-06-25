package ru.able.services.twin

import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{Keep, RunnableGraph, Tcp}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import ru.able.server.controllers.flow.model.FlowTypes.Basic

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import ru.able.server.controllers.flow.FlowFactory
import ru.able.server.controllers.flow.model.{LabelMapMessage, SimpleCommand}
import ru.able.server.controllers.flow.protocol.{MessageProtocol, SingularCommand}
import ru.able.services.detector.DetectorController
import ru.able.services.session.model.SessionController.{DeviceTwinCreated, DeviceTwinUpdated, RegisterNewDeviceTwin, ResetDeviceTwin, SessionNotChanged, SessionNotFound, SessionUpdated, TwinControllerRequest, TwinControllerResponse}
import ru.able.services.twin.model.DeviceTwinController.{DeviceTwin, TwinID}

import scala.collection.mutable

object DeviceTwinController {
  private var _instance: Option[DeviceTwinController] = None

  def apply()(implicit system: ActorSystem, ec: ExecutionContext): DeviceTwinController = _instance.getOrElse {
    _instance = Some(new DeviceTwinController)
    _instance.get
  }
}

final class DeviceTwinController private (implicit system: ActorSystem, ec: ExecutionContext) extends LazyLogging
{
//  private val _detectorController = DetectorController()
//  private val _flowController = new FlowController(_detectorController)

  private val _twinConnections = new mutable.HashMap[InetSocketAddress, TwinID]()
  private val _twinMap = new mutable.HashMap[TwinID, DeviceTwin]

//  def askSessionController(req: TwinControllerRequest): TwinControllerResponse = req match {
//    case RegisterNewDeviceTwin(conn) => createDeviceTwin(conn)
//    case ResetDeviceTwin(conn) => {
//      resetDeviceTwin(
//        conn,
//        _twinConnections.get(conn.remoteAddress).flatMap(_twinMap.get(_).map(_.sessionId)))
//    }
//  }

//  private def createDeviceTwin(conn: Tcp.IncomingConnection): DeviceTwinCreated = {
//    val (twinID, deviceTwin) = getOrRegisterDeviceTwin(conn)
//    val (publisher, workFlow) = _flowController.getFlowByType(deviceTwin.flowType)
//
//    _twinMap.update(twinID, DeviceTwin(deviceTwin.sessionId, deviceTwin.flowType, Some(publisher)))
//
//    DeviceTwinCreated(
//      conn
//        .flow
//        .viaMat(KillSwitches.single)(Keep.right)
//        .joinMat(workFlow)(Keep.both)
//    )
//  }

  def requestDeviceUUID(remoteAddress: InetSocketAddress): Unit = {
    _twinConnections.get(remoteAddress).map { twinID =>
      _twinMap.get(twinID).map { deviceTwin =>
        deviceTwin.commandPublisher.map { publisher =>
          publisher ! SingularCommand(SimpleCommand(MessageProtocol.UUID, ""))
        }
      }
    }
  }

//  def sendLabelMap(remoteAddress: InetSocketAddress)(implicit system: ActorSystem, ec: ExecutionContext): Unit = {
//    implicit val askTimeout = Timeout(Duration(15, TimeUnit.SECONDS))
//
//    _twinConnections.get(remoteAddress).map { twinID =>
//      _twinMap.get(twinID).map { deviceTwin =>
//        deviceTwin.commandPublisher.map { publisher =>
//          val future = (_detectorController ? "getDictionary").mapTo[Map[Int, String]]
//          try {
//            Await.result(future, askTimeout.duration) match {
//              case data: Map[Int, String] => publisher ! SingularCommand(LabelMapMessage(data))
//              case e => println("unfortunately")
//            }
//          } catch {
//            case e: Throwable => println("unfortunately")
//          }
//        }
//      }
//    }
//  }

//  private def getOrRegisterDeviceTwin(conn: Tcp.IncomingConnection): (TwinID, DeviceTwin) = {
//    val twinID: TwinID = _twinConnections.get(conn.remoteAddress) match {
//      case Some(value) => value
//      case _ => {
//        val twinUUID: TwinID = TwinID(UUID.randomUUID())
//        _twinConnections.update(conn.remoteAddress, twinUUID)
//        twinUUID
//      }
//    }
//
//    val deviceTwin = _twinMap.get(twinID) match {
//      case Some(value) => value
//      case _ => {
//        val sessionID = _sessionController.getOrCreateSession(conn)
//        val dt = DeviceTwin(sessionID, BasicFT, None)
//        _twinMap.update(twinID, dt)
//        dt
//      }
//    }
//
//    (twinID, deviceTwin)
//  }

//  private def resetDeviceTwin(conn: Tcp.IncomingConnection, sessionIDOpt: Option[SessionID]): TwinControllerResponse = {
//    val sessionID = _sessionController.resetSession(conn, sessionIDOpt) match {
//      case SessionUpdated(sid) => sid
//      case SessionNotChanged(sid) => sid
//      case SessionNotFound => _sessionController.getOrCreateSession(conn)
//    }
//
//    _twinConnections.get(conn.remoteAddress) match {
//      case Some(twinID) => _twinMap.update(twinID, DeviceTwin(sessionID, BasicFT, None))
//      case _ => _twinMap.update(TwinID(UUID.randomUUID()), DeviceTwin(sessionID, BasicFT, None))
//    }
//
//    DeviceTwinUpdated
//  }
}
