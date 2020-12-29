package ru.able.router.module

import akka.actor.ActorRef
import com.google.inject._
import com.google.inject.name.Names
import scala.concurrent.ExecutionContext

class RouterInjector extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[ExecutionContext])
      .annotatedWith(Names.named("MessageExecutionContext"))
      .toProvider(classOf[MessageExecutionContextProvider])

    bind(classOf[ActorRef])
      .annotatedWith(Names.named("RouterFSM"))
      .toProvider(classOf[RouterFSMProvider])
      .asEagerSingleton()

    bind(classOf[ActorRef])
      .annotatedWith(Names.named("CameraFSM"))
      .toProvider(classOf[CameraFSMProvider])

    bind(classOf[ActorRef])
      .annotatedWith(Names.named("SwitchFSM"))
      .toProvider(classOf[SwitchProvider])
  }
}
