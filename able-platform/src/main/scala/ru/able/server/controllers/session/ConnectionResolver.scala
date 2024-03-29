package ru.able.server.controllers.session

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.scaladsl.Tcp
import akka.util.Timeout
import ru.able.server.controllers.flow.model.SimpleCommand
import ru.able.server.controllers.flow.protocol.{MessageProtocol, SingularCommand}
import ru.able.server.controllers.gateway.model.GatewayModel.{GatewayResponse, GatewayRouted, RunCustomGateway}
import ru.able.server.controllers.session.model.KeeperModel.{ResolveConnection, SessionID}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object ConnectionResolver {
  def apply()(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props(new ConnectionResolver), "DeviceResolverActor")

  def props(sessionKeeperActor: ActorRef = Actor.noSender, gatewayActor: ActorRef = Actor.noSender): Props = {
    Props(new ConnectionResolver(sessionKeeperActor, gatewayActor))
  }

  def createActorPool(sessionKeeper: ActorRef, gateway: ActorRef)(implicit context: ActorContext): ActorRef = {
    context.actorOf(
      ConnectionResolver
        .props(sessionKeeper, gateway)
        .withDispatcher("deviceResolverThreadPool"),
      "DeviceResolverActorPool")
  }
}

final class ConnectionResolver(_sessionKeeperActor: ActorRef = Actor.noSender,
                               _gatewayActor: ActorRef = Actor.noSender) extends Actor with ActorLogging
{
  implicit val askActorTimeout = Timeout(Duration(1, TimeUnit.SECONDS))

  override def receive: Receive = {
    case ResolveConnection(connection, sessionID) => resolveConnection(connection, sessionID)
    case request => log.warning(s"DeviceResolver cannot parse incoming request: $request")
  }

  private def resolveConnection(connection: Tcp.IncomingConnection, sessionID: SessionID): Unit =
    (_gatewayActor ? RunCustomGateway(sessionID, connection)).mapTo[GatewayResponse].onComplete {
      case Success(GatewayRouted(publisher)) =>
        publisher ! SingularCommand(SimpleCommand(MessageProtocol.UUID, ""))
      case Failure(ex) =>
        log.warning(s"Publisher resolving for connection: ${connection.remoteAddress} failed with exception: $ex")
    }(context.dispatcher)
}