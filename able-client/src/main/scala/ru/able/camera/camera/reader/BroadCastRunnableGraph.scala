package ru.able.camera.camera.reader

import akka.stream.Materializer
import akka.stream.scaladsl.RunnableGraph

import ru.able.camera.camera.graph.CameraReaderGraph.CameraSource

case class BroadCastRunnableGraph(graph: RunnableGraph[CameraSource])(implicit val materializer: Materializer) {

  lazy val mat: CameraSource = graph.run()
}
