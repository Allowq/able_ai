package ru.able.camera.framereader

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.google.inject.Inject
import com.google.inject.name.Named
import com.typesafe.scalalogging.LazyLogging
import ru.able.camera.framereader.graph.broadcast.{BroadcastRunnableGraph, SourceBroadCastFactory}
import ru.able.camera.framereader.graph.source.CameraReaderGraphFactory
import ru.able.camera.utils.settings.Settings
import ru.able.router.model.Orchestrator.GlobalKillSwitch

import scala.concurrent.{Future, Promise, TimeoutException}
import scala.util.{Failure, Success, Try}

class BroadcastMaterializer @Inject()(@Named("CameraReaderFactory") cameraReaderFactory: CameraReaderGraphFactory,
                                      broadcastFactory: SourceBroadCastFactory,
                                      settings: Settings)
                                     (implicit val materializer: Materializer) extends LazyLogging
{
  private implicit val executionContext = materializer.system.dispatchers.defaultGlobalDispatcher

  def create(gks: GlobalKillSwitch): Future[BroadcastRunnableGraph] = {
    val reader    = cameraReaderFactory.create(gks)
    val broadcast = broadcastFactory.create(reader)
    val promise   = Promise[BroadcastRunnableGraph]()

    materalize(broadcast, promise)
    promise.future
  }

  private def materalize(broadcast: BroadcastRunnableGraph, promise: Promise[BroadcastRunnableGraph]) =
    Try(awaitBroadcastStartUp(broadcast, promise)) recover {
      case e: TimeoutException => promise failure e
      case e: Exception        => promise failure e
    }

  private def awaitBroadcastStartUp(broadcast: BroadcastRunnableGraph, promise: Promise[BroadcastRunnableGraph]) =
  {
    val broadcastStream = broadcast.mat.take(1).runWith(
      Sink.foreach(
        f => logger.info(s"Grab frame with sample description: $f")
      )
    )

    broadcastStream.onComplete {
      case Success(Done) => promise success broadcast
      case Failure(e) => {
        logger.error("-----------")
        logger.error(e.getMessage, e)
        promise failure e
      }
    }
  }
}
