package ru.able.communication.viatcp.module

import akka.actor.ActorRef
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import ru.able.communication.viatcp.stage.bridge.ReactiveBridge
import ru.able.communication.viatcp.EventBus

import scala.concurrent.ExecutionContext

class ConnViaTCPInjector extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[EventBus])
      .annotatedWith(Names.named(EventBus.providerName))
      .to(classOf[EventBus])
      .asEagerSingleton()

    bind(classOf[ExecutionContext])
      .annotatedWith(Names.named("MessageExecutionContext"))
      .toProvider(classOf[MessageExecutionContextProvider])

    bind(classOf[ActorRef])
      .annotatedWith(Names.named(ReactiveBridge.ProviderName))
      .toProvider(classOf[TCPBridgeProvider])
      .asEagerSingleton()
  }
}
