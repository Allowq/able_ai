package ru.able.client.module

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Named
import com.google.inject.{Inject, Provider}
import ru.able.camera.utils.settings.Settings
import ru.able.client.NetworkClient
import ru.able.client.pipeline.FrameSeqHandler
import ru.able.client.protocol.SimpleMessage

import scala.concurrent.ExecutionContext

class NetworkClientProvider @Inject() (settings: Settings,
                                       system: ActorSystem,
                                       @Named("MessageExecutionContext") ec: ExecutionContext) extends Provider[ActorRef]
{
  override def get(): ActorRef =
    system.actorOf(NetworkClient.props(settings, FrameSeqHandler, SimpleMessage.protocol)(system, ec), NetworkClient.ProviderName)
}
