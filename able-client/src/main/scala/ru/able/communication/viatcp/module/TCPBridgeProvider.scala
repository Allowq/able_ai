package ru.able.communication.viatcp.module

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Named
import com.google.inject.{Inject, Provider}
import ru.able.camera.utils.settings.Settings
import ru.able.communication.viatcp.protocol.MessageProtocol
import ru.able.communication.viatcp.EventBus
import ru.able.communication.viatcp.stage.FrameSeqHandler
import ru.able.communication.viatcp.stage.bridge.ReactiveBridge

import scala.concurrent.ExecutionContext

class TCPBridgeProvider @Inject()(settings: Settings,
                                  system: ActorSystem,
                                  @Named("MessageExecutionContext") ec: ExecutionContext,
                                  @Named("EventBus") eventBus: EventBus) extends Provider[ActorRef]
{
  override def get(): ActorRef =
    system.actorOf(
      ReactiveBridge.props(settings, FrameSeqHandler, MessageProtocol.protocol, eventBus)(system, ec),
      ReactiveBridge.ProviderName
    )
}
