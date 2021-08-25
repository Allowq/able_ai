package ru.able.system.module

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.AbstractModule
import com.google.inject.name.Names

import ru.able.system.SystemInitializer

import scala.concurrent.ExecutionContext

class SystemInitializerInjector(system: ActorSystem, materializer: Materializer) extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[ActorSystem]).toInstance(system)
    bind(classOf[Materializer]).toInstance(materializer)

    bind(classOf[ExecutionContext])
      .annotatedWith(Names.named("StartUpExecutionContext"))
      .toProvider(classOf[StartUpExecutionContextProvider])

    bind(classOf[ActorRef])
      .annotatedWith(Names.named(SystemInitializer.Name))
      .toProvider(classOf[SystemInitializerProvider])
      .asEagerSingleton()
  }
}
