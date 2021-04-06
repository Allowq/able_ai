package ru.able.camera.camera.reader

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Sink
import com.google.inject.Inject
import com.google.inject.name.Named
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Promise
import scala.concurrent.TimeoutException
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import ru.able.camera.camera.graph.CameraReaderGraph
import ru.able.camera.camera.graph.factory.CameraReaderGraphFactory
import ru.able.camera.camera.graph.factory.SourceBroadCastFactory
import ru.able.common.Switches.GlobalKillSwitch
import ru.able.camera.utils.settings.Settings

object BroadcastMaterializer {
  val StreamClosedError = "Stream unexpectedly stopped."
  type BroadCast = RunnableGraph[CameraReaderGraph.CameraSource]
}

class BroadcastMaterializer @Inject()(@Named("CameraReaderFactory") cameraReaderFactory: CameraReaderGraphFactory,
                                      broadcastFactory: SourceBroadCastFactory,
                                      settings: Settings)
                                     (implicit val materializer: Materializer) extends LazyLogging
{
  private implicit val executionContext = materializer.system.dispatchers.defaultGlobalDispatcher

  def create(gks: GlobalKillSwitch): Promise[BroadCastRunnableGraph] = {
    val reader    = cameraReaderFactory.create(gks)
    val broadcast = broadcastFactory.create(reader)
    val promise   = Promise[BroadCastRunnableGraph]()
    materalize(broadcast, promise)

    promise
  }

  private def materalize(broadcast: BroadCastRunnableGraph, promise: Promise[BroadCastRunnableGraph]) =
    Try(awaitBroadcastStartUp(broadcast, promise)) recover {
      case e: TimeoutException => promise failure e
      case e: Exception        => promise failure e
    }

  private def awaitBroadcastStartUp(broadcast: BroadCastRunnableGraph, promise: Promise[BroadCastRunnableGraph]) =
  {
    val broadcastStream = broadcast.mat.take(1).runWith(
      Sink.foreach(
        f => logger.info(s"Grab frame with sample description: $f")
      )
    )

    broadcastStream.onComplete {
      case Success(Done) => promise success broadcast
//        promise failure new RuntimeException(StreamClosedError)
      case Failure(e) => {
        logger.error("-----------")
        logger.error(e.getMessage,e)
        promise failure e
      }
    }
  }
}
