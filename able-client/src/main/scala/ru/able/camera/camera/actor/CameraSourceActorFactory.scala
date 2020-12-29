package ru.able.camera.camera.actor

import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.stream.Materializer
import com.google.inject.Inject
import ru.able.camera.camera.reader.BroadcastMaterializer

@deprecated
class CameraSourceActorFactory @Inject()(broadCastMateralizer: BroadcastMaterializer, materalizer: Materializer) {

  def create()(implicit context: ActorContext): ActorRef =
    context.actorOf(CameraSourceActor.props(broadCastMateralizer, materalizer), CameraSourceActor.Name)

}
