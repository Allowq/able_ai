package ru.able.camera.framereader.graph.broadcast

import akka.stream.Materializer
import akka.stream.scaladsl.RunnableGraph
import ru.able.camera.framereader.graph.source.CameraReaderGraph.CameraSource

case class BroadcastRunnableGraph(graph: RunnableGraph[CameraSource])(implicit val materializer: Materializer) {

  lazy val mat: CameraSource = graph.run()
}
