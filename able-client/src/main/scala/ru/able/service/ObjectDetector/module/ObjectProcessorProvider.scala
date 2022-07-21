package ru.able.service.ObjectDetector.module

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.{Inject, Provider}
import com.google.inject.name.Named
import ru.able.communication.viatcp.EventBus
import ru.able.service.ObjectDetector.ObjectProcessor

class ObjectProcessorProvider @Inject()(system: ActorSystem,
                                        @Named("ReactiveBridge") bridge: ActorRef,
                                        @Named("EventBus") eventBus: EventBus) extends Provider[ActorRef]
{
  override def get(): ActorRef = system.actorOf(ObjectProcessor.props(bridge, eventBus), ObjectProcessor.ProviderName)
}
