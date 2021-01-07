package ru.able.camera.camera.module

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.google.inject.{Inject, Provider}
import ru.able.camera.camera.actor.CameraSourceActor
import ru.able.camera.camera.reader.BroadcastMaterializer

class CameraSourceProvider @Inject()(system: ActorSystem,
                                     materalizer: Materializer,
                                     broadCastMateralizer: BroadcastMaterializer) extends Provider[ActorRef]
{
  override def get(): ActorRef =
    system.actorOf(CameraSourceActor.props(broadCastMateralizer, materalizer))
}
