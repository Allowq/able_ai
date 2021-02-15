package ru.able.server

import akka.actor.ActorSystem
import akka.stream.{FlowShape, Materializer}
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, Sink, Source, Tcp}
import akka.util.ByteString
import ru.able.server.pipeline.{Processor, Resolver}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ServerBase {
  def apply[Cmd, Evt](interface: String,
                      port: Int,
                      resolver: Resolver[Evt],
                      protocol: BidiFlow[ByteString, Evt, Cmd, ByteString, Any])
                     (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext): Unit =
  {
    val handler = Sink.foreach[Tcp.IncomingConnection] { conn =>
      val processor = Processor[Cmd, Evt](resolver, 1, false)

      val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val pipeline = b.add(processor.flow.atop(protocol.reversed))

        pipeline.in1 <~ Source.empty
        pipeline.out2 ~> Sink.ignore

        FlowShape(pipeline.in2, pipeline.out1)
      })

      conn handleWith flow
    }

    val connections = Tcp().bind(interface, port, halfClose = true)
    val binding = connections.to(handler).run()

    binding.onComplete {
      case Success(b) ⇒ println("Bound to: " + b.localAddress)
      case Failure(e) ⇒
        system.terminate()
    }

    binding
  }
}
