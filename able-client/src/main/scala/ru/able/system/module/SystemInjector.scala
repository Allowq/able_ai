package ru.able.system.module

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import ru.able.communication.BasicCommunication
import ru.able.communication.Communication
import ru.able.communication.NotifierProvider
import ru.able.system.SystemInitializer

import scala.concurrent.ExecutionContext

class SystemInjector(system: ActorSystem, materalizer: Materializer) extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[ActorSystem]).toInstance(system)
    bind(classOf[Materializer]).toInstance(materalizer)

    bind(classOf[ExecutionContext])
      .annotatedWith(Names.named("StartUpEC"))
      .toProvider(classOf[StartUpExecutionContextProvider])

    bind(classOf[Communication])
      .to(classOf[BasicCommunication])

    bind(classOf[ActorRef])
      .annotatedWith(Names.named("Notifier"))
      .toProvider(classOf[NotifierProvider])

    bind(classOf[ActorRef])
      .annotatedWith(Names.named(SystemInitializer.Name))
      .toProvider(classOf[SystemInitilaizerProvider])
      .asEagerSingleton()
  }
}
