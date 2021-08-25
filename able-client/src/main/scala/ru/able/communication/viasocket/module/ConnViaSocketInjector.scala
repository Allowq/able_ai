package ru.able.communication.viasocket.module

import akka.actor.ActorRef
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import ru.able.communication.viasocket.{SocketCommunication, Communication, Notifier}

class ConnViaSocketInjector extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[Communication])
      .to(classOf[SocketCommunication])

    bind(classOf[ActorRef])
      .annotatedWith(Names.named(Notifier.ProviderName))
      .toProvider(classOf[NotifierProvider])
  }
}