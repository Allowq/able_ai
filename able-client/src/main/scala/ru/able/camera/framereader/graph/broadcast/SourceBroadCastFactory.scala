package ru.able.camera.framereader.graph.broadcast

import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep}
import com.google.inject.Inject
import ru.able.camera.framereader.graph.source.CameraReaderGraph.CameraSource

class SourceBroadCastFactory @Inject()(implicit materializer: Materializer)
{
  def create(source: CameraSource): BroadcastRunnableGraph = {
    BroadcastRunnableGraph(source.toMat(BroadcastHub.sink(bufferSize = 1))(Keep.right))
  }
}
