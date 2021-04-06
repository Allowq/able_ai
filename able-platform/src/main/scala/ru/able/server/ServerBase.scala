package ru.able.server

import akka.Done
import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.{KillSwitches, Materializer, UniqueKillSwitch}
import akka.stream.scaladsl.{BidiFlow, Keep, RunnableGraph, Sink, Tcp}
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.HashMap
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import ru.able.server.controllers.flow.FlowController
import ru.able.server.controllers.flow.model.{DetectionFT, FlowType}
import ru.able.services.session.SessionController
import ru.able.services.session.model.{CheckSessionExist, ResetSession, SessionNotFound, SessionRequest, SessionResponse, SessionUpdated}

final class ServerBase[Cmd, Evt] private (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends LazyLogging
{
  private var _interface: String = "127.0.0.1"
  private var _port: Int = 9999

  private val _connectionMap = new HashMap[String, RunnableGraph[(UniqueKillSwitch, Future[Done])]]

  private val _sessionController = system.actorOf(SessionController.props)
  private val _flowController = new FlowController()(system, ec)

  implicit val defaultAskTimeout = Timeout(1 seconds)

  def this(interface: String, port: Int)
          (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
  {
    this()(system, mat, ec)
    _interface = interface
    _port = port

    setupConnection(DetectionFT)
  }

  private def setupConnection(flowType: FlowType): Unit = {
    val handler = Sink.foreach[Tcp.IncomingConnection] { conn =>
      tellSessionController(CheckSessionExist(conn))

      val remoteAddr = conn.remoteAddress.toString
      _connectionMap.get(remoteAddr) match {
        case None => {
          _connectionMap.update(
            remoteAddr,
            conn
              .flow
              .viaMat(KillSwitches.single)(Keep.right)
              .joinMat(_flowController.getFlowByType(flowType))(Keep.both)
          )

          _connectionMap.apply(remoteAddr).run() match {
            case (killSwitch, future) => {
              future.onComplete {
                case Success(msg) => {
                  logger.info(s"Stream for client with id $remoteAddr completed with msg: $msg")
                  tellSessionController(ResetSession(conn))
                  killSwitch.shutdown()
                  _connectionMap.remove(remoteAddr)
                }
                case Failure(ex) => {
                  logger.warn(s"Stream for client with id $remoteAddr closed with error: ${ex.toString}")
                  tellSessionController(ResetSession(conn))
                  killSwitch.abort(new Exception("Force stopped from outside!"))
                  _connectionMap.remove(remoteAddr)
                }
              }
            }
          }
        }
        case Some(_) => logger.info(s"Client with id: $remoteAddr has been connected yet")
      }
    }

    Tcp()
      .bind(_interface, _port, halfClose = true)
      .to(handler)
      .run()
      .onComplete {
        case Success(addr) => println("Bound to: " + addr.localAddress)
        case Failure(ex) => logger.warn(ex.toString)
      }
  }

  private def askSessionController(req: SessionRequest, sender: ActorRef = Actor.noSender): Unit = {
    _sessionController
      .ask(req)(defaultAskTimeout, sender)
      .mapTo[SessionResponse]
      .map {
        case r: SessionResponse => processSessionResponse(r)
        case ex => logger.warn(s"failed with $ex", ex)
      }
  }

  private def tellSessionController(req: SessionRequest, sender: ActorRef = Actor.noSender): Unit = {
    _sessionController.tell(req, sender)
  }

  private def processSessionResponse(resp: SessionResponse): Unit = resp match {
    case SessionUpdated =>
    case SessionNotFound =>
  }
}