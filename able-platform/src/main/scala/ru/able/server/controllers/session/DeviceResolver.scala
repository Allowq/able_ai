package ru.able.server.controllers.session

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.Tcp
import akka.pattern.ask
import akka.util.Timeout

import ru.able.server.controllers.flow.model.SimpleCommand
import ru.able.server.controllers.flow.protocol.{Action, ConsumerAction, MessageProtocol, SingularCommand}
import ru.able.server.controllers.gateway.model.GatewayModel.{GatewayResponse, GatewayRouted, RunCustomGateway}
import ru.able.server.controllers.session.model.KeeperModel.{DeviceID, ResolveConnection, ResolveDeviceID, SessionID}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object DeviceResolver {
  def apply()(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props(new DeviceResolver), "DeviceResolverActor")

  def props(sessionKeeperActor: ActorRef = Actor.noSender, gatewayActor: ActorRef = Actor.noSender): Props = {
    Props(new DeviceResolver(sessionKeeperActor, gatewayActor))
  }

  def createActorPool(sessionKeeper: ActorRef, gateway: ActorRef)(implicit system: ActorSystem): ActorRef = {
    system.actorOf(
      DeviceResolver
        .props(sessionKeeper, gateway)
        .withDispatcher("deviceResolverThreadPool"),
      "DeviceResolverActorPool")
  }
}

final class DeviceResolver(_sessionKeeperActor: ActorRef = Actor.noSender,
                           _gatewayActor: ActorRef = Actor.noSender) extends Actor with ActorLogging
{
  implicit val askActorTimeout = Timeout(Duration(1, TimeUnit.SECONDS))

  override def receive: Receive = {
    case ResolveConnection(connection, sessionID) => resolveConnection(connection, sessionID)
    case request => log.warning(s"DeviceResolver cannot parse incoming request: $request")
  }

  private def resolveConnection(connection: Tcp.IncomingConnection, sessionID: SessionID): Unit = {
    def processor(payload: AnyRef): Action = {
      Try {
        val deviceID: String = payload.asInstanceOf[String]

        _sessionKeeperActor ! ResolveDeviceID(
          connection,
          DeviceID(Some(UUID.fromString(deviceID)))
        )
      } recover {
        case ex: Exception => log.warning(s"Parsing DeviceID was failed with exception: $ex.")
      }
      ConsumerAction.AcceptSignal
    }

    Try {
      val askPublisher = (_gatewayActor ? RunCustomGateway(sessionID, connection, processor)).mapTo[GatewayResponse]
      Await.result(askPublisher, askActorTimeout.duration) match {
        case GatewayRouted(publisher) => publisher ! SingularCommand(SimpleCommand(MessageProtocol.UUID, ""))
      }
    } match {
      case Success(_) => log.info(s"Resolving command for host: ${connection.remoteAddress} sanded.")
      case Failure(ex) => log.warning(s"Publisher resolving for connection: ${connection.remoteAddress} failed with exception: $ex")
    }
  }
}