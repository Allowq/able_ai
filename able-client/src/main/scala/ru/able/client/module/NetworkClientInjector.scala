package ru.able.client.module

import akka.actor.ActorRef
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import ru.able.router.module.MessageExecutionContextProvider

import scala.concurrent.ExecutionContext

class NetworkClientInjector extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[ExecutionContext])
      .annotatedWith(Names.named("MessageExecutionContext"))
      .toProvider(classOf[MessageExecutionContextProvider])

    bind(classOf[ActorRef])
      .annotatedWith(Names.named("NetworkClient"))
      .toProvider(classOf[NetworkClientProvider])
      .asEagerSingleton()
  }
}
