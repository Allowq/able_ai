package ru.able.router.module

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Named
import com.google.inject.{Inject, Provider}
import ru.able.camera.utils.settings.Settings
import ru.able.router.SwitchFSM

import scala.concurrent.ExecutionContext

class SwitchProvider @Inject()(
                                @Named("SystemInitializer") systemInitializer: ActorRef,
                                settings: Settings,
                                system: ActorSystem,
                                @Named("MessageExecutionContext") ec: ExecutionContext
) extends Provider[ActorRef] {

  override def get(): ActorRef =
    system.actorOf(SwitchFSM.props(systemInitializer, settings)(ec), SwitchFSM.Name)

}
