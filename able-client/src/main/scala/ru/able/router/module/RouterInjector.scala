package ru.able.router.module

import akka.actor.ActorRef
import com.google.inject._
import com.google.inject.name.Names
import scala.concurrent.ExecutionContext

class RouterInjector extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[ActorRef])
      .annotatedWith(Names.named("PluginRegistryFSM"))
      .toProvider(classOf[PluginRegistryProvider])
      .asEagerSingleton()

    bind(classOf[ActorRef])
      .annotatedWith(Names.named("SwitchFSM"))
      .toProvider(classOf[SwitchProvider])
  }
}
