package ru.able.camera.camera.graph.factory

import akka.stream.Materializer
import akka.stream.scaladsl.BroadcastHub
import akka.stream.scaladsl.Keep
import com.google.inject.Inject
import ru.able.camera.camera.graph.CameraReaderGraph.CameraSource
import ru.able.camera.camera.reader.BroadCastRunnableGraph

class SourceBroadCastFactory @Inject()(implicit materializer: Materializer)
{
  def create(source: CameraSource): BroadCastRunnableGraph = {
    BroadCastRunnableGraph(
      source.toMat(BroadcastHub.sink(bufferSize = 1))(Keep.right)
    )
  }
}
