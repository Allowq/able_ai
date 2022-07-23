package ru.able.server

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Tcp}
import com.typesafe.scalalogging.LazyLogging
import ru.able.server.controllers.session.model.KeeperModel.{NewDeviceConnection, NewNotifierConnection}
import ru.able.services.session.SessionKeeper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final class ServerBase[Cmd, Evt](val interface: String = "127.0.0.1", val port: Int = 9999)
                                (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends LazyLogging
{
  private val _sessionKeeperActor: ActorRef = SessionKeeper()

  private def setupConnection: Unit = {
    val handler: Sink[Tcp.IncomingConnection, Future[Done]] =
      Sink.foreach[Tcp.IncomingConnection] { conn =>
        _sessionKeeperActor ! NewDeviceConnection(conn)
      }

    val connections = Tcp().bind(interface, port, halfClose = false)
    val binding = connections.to(handler).run()

    binding.onComplete {
      case Success(addr) => logger.info(s"Server started, listening on: ${addr.localAddress}")
      case Failure(ex) => logger.warn(s"Server could not bind to $interface:$port and failed with exception: ${ex.toString}")
    }
  }

  setupConnection
}