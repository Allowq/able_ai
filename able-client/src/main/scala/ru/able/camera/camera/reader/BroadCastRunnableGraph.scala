package ru.able.camera.camera.reader

import java.util.concurrent.TimeoutException

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.{RunnableGraph, Sink}
import ru.able.camera.camera.graph.CameraReaderGraph.CameraSource

import scala.concurrent.Future

case class BroadCastRunnableGraph(graph: RunnableGraph[CameraSource])(implicit val materializer: Materializer) {

  lazy val mat: CameraSource = graph.run()

  @throws[TimeoutException]
  def toFuture(): Future[Done] =
    graph.run().runWith(Sink.ignore)
}
