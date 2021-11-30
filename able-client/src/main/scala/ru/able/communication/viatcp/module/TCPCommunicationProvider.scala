package ru.able.communication.viatcp.module

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Named
import com.google.inject.{Inject, Provider}
import ru.able.camera.utils.settings.Settings
import ru.able.communication.viatcp.protocol.MessageProtocol
import ru.able.communication.viatcp.{TCPEventBus, TCPCommunication}
import ru.able.communication.viatcp.stage.FrameSeqHandler

import scala.concurrent.ExecutionContext

class TCPCommunicationProvider @Inject()(settings: Settings,
                                         system: ActorSystem,
                                         @Named("MessageExecutionContext") ec: ExecutionContext,
                                         @Named("TCPEventBus") eventBus: TCPEventBus) extends Provider[ActorRef]
{
  override def get(): ActorRef =
    system.actorOf(
      TCPCommunication.props(settings, FrameSeqHandler, MessageProtocol.protocol, eventBus)(system, ec),
      TCPCommunication.ProviderName
    )
}
