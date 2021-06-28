package ru.able.server

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Tcp}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import ru.able.server.controllers.session.model.KeeperModel.NewConnection
import ru.able.services.session.SessionKeeper

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

final class ServerBase[Cmd, Evt](val interface: String = "127.0.0.1", val port: Int = 9999)
                                (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends LazyLogging
{
  implicit lazy val defaultAskTimeout = Timeout(1 seconds)

  private val _sessionKeeperActor: ActorRef = SessionKeeper()

  private def setupConnection: Unit = {
    val handler = Sink.foreach[Tcp.IncomingConnection] { connection =>
      _sessionKeeperActor ! NewConnection(connection)
    }

    Tcp()
      .bind(interface, port, halfClose = true)
      .to(handler)
      .run()
      .onComplete {
        case Success(addr) => logger.info(s"Bound to: ${addr.localAddress}")
        case Failure(ex) => logger.warn(ex.toString)
      }
  }

  setupConnection
}