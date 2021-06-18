package ru.able.server

import java.net.InetSocketAddress

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{Materializer, UniqueKillSwitch}
import akka.stream.scaladsl.{RunnableGraph, Sink, Tcp}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.HashMap
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import ru.able.services.session.model.SessionController.{DeviceTwinCreated, RegisterNewDeviceTwin, ResetDeviceTwin, TwinControllerRequest, TwinControllerResponse}
import ru.able.services.twin.DeviceTwinController

final class ServerBase[Cmd, Evt](val interface: String = "127.0.0.1", val port: Int = 9999)
                                (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends LazyLogging
{
  implicit val defaultAskTimeout = Timeout(1 seconds)

  private val _connectionMap = new HashMap[InetSocketAddress, RunnableGraph[(UniqueKillSwitch, Future[Done])]]
  private val _deviceTwinController = DeviceTwinController()

  private def setupConnection: Unit = {
    val handler = Sink.foreach[Tcp.IncomingConnection] { conn =>
      _connectionMap.get(conn.remoteAddress) match {
        case None => {
          logger.info(s"New client with host address: ${conn.remoteAddress} connected.")
          createAndRunDeviceTwin(conn)
        }
        case Some(_) => {
          logger.warn(s"Client with host address: ${conn.remoteAddress} has been connected yet. Reset session!")
          _connectionMap.remove(conn.remoteAddress)
        }
      }
    }

    Tcp()
      .bind(interface, port, halfClose = true)
      .to(handler)
      .run()
      .onComplete {
        case Success(addr) => println(s"Bound to: ${addr.localAddress}")
        case Failure(ex) => logger.warn(ex.toString)
      }
  }

  private def createAndRunDeviceTwin(conn: Tcp.IncomingConnection): Unit = {
    val DeviceTwinCreated(graph) = askTwinController(RegisterNewDeviceTwin(conn))

    _connectionMap.update(conn.remoteAddress, graph)
    _connectionMap.apply(conn.remoteAddress).run() match {
      case (killSwitch, future) => {
        future.onComplete {
          case Success(msg) => {
            logger.info(s"Stream for client with id ${conn.remoteAddress} completed with msg: $msg")
            askTwinController(ResetDeviceTwin(conn))
            killSwitch.shutdown()
            _connectionMap.remove(conn.remoteAddress)
          }
          case Failure(ex) => {
            logger.warn(s"Stream for client with id ${conn.remoteAddress} closed with error: ${ex.toString}")
            askTwinController(ResetDeviceTwin(conn))
            killSwitch.abort(new Exception("Force stopped from outside!"))
            _connectionMap.remove(conn.remoteAddress)
          }
        }
      }
    }

    // TODO: Testing
    Future {
      Thread.sleep(2000)
      _deviceTwinController.requestDeviceUUID(conn.remoteAddress)
//      _deviceTwinController.sendLabelMap(conn.remoteAddress)
    }
  }

  private def askTwinController(req: TwinControllerRequest): TwinControllerResponse =
    _deviceTwinController.askSessionController(req)

  setupConnection
}