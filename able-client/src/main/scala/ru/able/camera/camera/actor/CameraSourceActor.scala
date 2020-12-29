package ru.able.camera.camera.actor

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.stream._
import com.typesafe.scalalogging.LazyLogging
import ru.able.camera.camera.reader.BroadcastMaterializer
import ru.able.router.messages.Error
import ru.able.router.messages.SourceInit
import ru.able.router.messages.Start

import scala.util.Failure
import scala.util.Success
import scala.util.Try

object CameraSourceActor {
  val Name = classOf[CameraSourceActor].getName

  def props(broadCastMateralizer: BroadcastMaterializer, materalizer: Materializer) = {
    Props(new CameraSourceActor(broadCastMateralizer)(materalizer))
  }
}

/**
  * Managing camera source creation
  *
  * @param broadCastMateralizer factory to materalize broadcast stream from source
  */
@deprecated
class CameraSourceActor(broadCastMateralizer: BroadcastMaterializer)(implicit val materalizer: Materializer)
    extends Actor
    with ActorLogging
    with LazyLogging {

  private implicit val executionContext =
    materalizer.system.dispatchers.defaultGlobalDispatcher

  override def receive: Receive = {
    case Start(ks) =>
      val requestor = sender()
//      Try(broadCastMateralizer.create(ks).future.onComplete {
//        case Success(bs) => requestor ! SourceInit(bs)
//        case Failure(e)  => respondWithError(requestor, e)
//      }) recover {
//        case e: Exception => respondWithError(requestor, e)
//      }
  }

  private def respondWithError(requestor: ActorRef, e: Throwable) = {
    log.error("Error occurred while starting source: {}", e)
    requestor ! Error(e.getMessage)
  }
}
