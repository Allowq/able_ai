package ru.able.communication

import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.google.inject.Inject
import com.google.inject.Provider

import ru.able.camera.utils.settings.Settings

class NotifierProvider @Inject()(system: ActorSystem,
                                 settings: Settings,
                                 communication: Communication) extends Provider[ActorRef]
{
  override def get(): ActorRef = {
    system.actorOf(Notifier.props(settings, communication)(system), "Notifier")
  }
}
