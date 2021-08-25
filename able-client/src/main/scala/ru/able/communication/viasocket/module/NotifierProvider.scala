package ru.able.communication.viasocket.module

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.{Inject, Provider}
import ru.able.camera.utils.settings.Settings
import ru.able.communication.viasocket.{Communication, Notifier}

class NotifierProvider @Inject()(system: ActorSystem,
                                 settings: Settings,
                                 communication: Communication) extends Provider[ActorRef]
{
  override def get(): ActorRef = {
    system.actorOf(Notifier.props(settings, communication)(system), Notifier.ProviderName)
  }
}
