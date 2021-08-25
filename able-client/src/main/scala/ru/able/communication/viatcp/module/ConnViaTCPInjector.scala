package ru.able.communication.viatcp.module

import akka.actor.ActorRef
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import ru.able.communication.viatcp.TCPCommunication

import scala.concurrent.ExecutionContext

class ConnViaTCPInjector extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[ExecutionContext])
      .annotatedWith(Names.named("MessageExecutionContext"))
      .toProvider(classOf[MessageExecutionContextProvider])

    bind(classOf[ActorRef])
      .annotatedWith(Names.named(TCPCommunication.ProviderName))
      .toProvider(classOf[TCPCommunicationProvider])
      .asEagerSingleton()
  }
}
