package ru.able.service.ObjectDetector.module

import akka.actor.ActorRef
import com.google.inject.AbstractModule
import com.google.inject.name.Names

import ru.able.service.ObjectDetector.ObjectProcessor

class ObjectProcessorInjector extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[ActorRef])
      .annotatedWith(Names.named(ObjectProcessor.ProviderName))
      .toProvider(classOf[ObjectProcessorProvider])
      .asEagerSingleton()
  }
}
