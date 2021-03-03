package ru.able.server

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, FlowShape, Materializer, SinkShape}
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Tcp}
import akka.stream.stage.GraphStage
import akka.util.ByteString

import scala.collection.mutable.HashMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import ru.able.detector.Detector
import ru.able.detector.controller.DetectorController
import ru.able.detector.stage.ShowOriginalEventStage
import ru.able.server.pipeline.{ConsumerStage, Processor, ProducerStage, Resolver}
import ru.able.server.protocol.{Command, Event, ProducerAction, SimpleMessage, SingularCommand, SingularEvent, StreamEvent, StreamingCommand}

object ServerBase {
  def apply[Cmd, Evt](interface: String,
                      port: Int,
                      resolver: Resolver[Evt],
                      protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any])
                     (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext): Unit = {
    val handler = Sink.foreach[Tcp.IncomingConnection] { conn =>
      println(conn.remoteAddress.getPort.toString)

      val processor = Processor[Cmd, Evt](resolver, 1, false)
      val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val pipeline = b.add(processor.flow.atop(protocol))

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
  }

  def applyModern[Cmd, Evt](interface: String,
                            port: Int,
                            resolver: Resolver[Evt],
                            protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any])
                           (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext): Unit = {
    val detectorController = system.actorOf(DetectorController.props)

    val handler = Sink.foreach[Tcp.IncomingConnection] { conn =>
      println(conn.remoteAddress.getPort.toString)

      val detector = Detector[Cmd, Evt](detectorController)
      val processor = Processor[Cmd, Evt](resolver, 1, true)
      val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val pipeline = b.add(processor.flow.atop(protocol))
        val processing = b.add(detector.flow)

        pipeline.out2 ~> processing
        processing ~> pipeline.in1

        FlowShape(pipeline.in2, pipeline.out1)
      })

      conn.handleWith(flow)
    }

    val connections = Tcp().bind(interface, port, halfClose = true)
    val binding = connections.to(handler).run()

    binding.onComplete {
      case Success(b) ⇒ println("Bound to: " + b.localAddress)
      case Failure(e) ⇒
        system.terminate()
    }
  }
}



final class ServerBase[Cmd, Evt] private (val _resolver: Resolver[Evt],
                                          val _protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any],
                                          val _signalOutStage: Option[GraphStage[SinkShape[Event[Evt]]]] = None)
                                         (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) {
  private var _interface: String = "127.0.0.1"
  private var _port: Int = 9999

  private var _binding: Option[Future[Tcp.ServerBinding]] = None
  private var _handler: Option[Sink[Tcp.IncomingConnection, Future[Done]]] = None
  private val _connectionMap = new HashMap[String, GraphStage[SinkShape[Event[Evt]]]]

  def this(customResolver: Resolver[Evt],
           protocol: BidiFlow[Cmd, ByteString, ByteString, Evt, Any])
          (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
  {
    this(customResolver, protocol, None)(system, mat, ec)
  }

  def setupConnection(interface: String, port: Int): Boolean = {
    _handler = Some(Sink.foreach[Tcp.IncomingConnection] { conn =>
      val clientId = conn.remoteAddress.toString
      val processor = Processor[Cmd, Evt](_resolver, 1, true)

      val stage = _connectionMap.get(clientId) match {
        case Some(value) => value
        case _ => {
          _connectionMap.update(clientId, ShowOriginalEventStage(clientId))
          _connectionMap.apply(clientId)
        }
      }

      val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val pipeline = b.add(processor.flow.atop(_protocol))

        pipeline.in1 <~ Source.empty
        pipeline.out2 ~> stage

        FlowShape(pipeline.in2, pipeline.out1)
      })
      conn handleWith flow
    })

    _binding = Some(
      Tcp()
        .bind(interface, port, halfClose = true)
        .to(_handler.get).run()
    )

    _binding match {
      case Some(b) => {
        b.onComplete {
          case Success(addr) => {
            println("Bound to: " + addr.localAddress)
            _interface = interface
            _port = port
            b.isCompleted
          }
          case Failure(ex) => println(ex)
        }
        b.isCompleted
      }
      case None => false
    }
  }
}