package ru.able.communication.viatcp.stage

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BidiFlow, GraphDSL, RunnableGraph, Tcp}
import akka.stream.stage.GraphStageLogic.EagerTerminateOutput
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler, TimerGraphStageLogic}
import akka.stream.{Attributes, BidiShape, ClosedShape, FlowShape, Inlet, Materializer, Outlet}
import akka.util.ByteString
import ru.able.communication.viatcp.protocol.{Command, Event}
import ru.able.communication.viatcp.stage.ClientStage.{ConnectionClosedWithReasonException, ConnectionClosedWithoutReasonException, HostDown, HostEvent, HostUp, NoConnectionsAvailableException}

import scala.collection.mutable
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

case class Host(host: String, port: Int)

object ClientStage {

  trait ConnectionClosedException

  trait HostEvent {
    def host: Host
  }

  case class ConnectionClosedWithReasonException(message: String, cause: Throwable)
    extends Exception(message, cause) with ConnectionClosedException

  case class ConnectionClosedWithoutReasonException(message: String)
    extends Exception(message) with ConnectionClosedException

  case class HostUp(host: Host) extends HostEvent
  case class HostDown(host: Host) extends HostEvent

  case object NoConnectionsAvailableException extends Exception
}

class ClientStage[Context, Cmd, Evt](connectionsPerHost: Int,
                                     maximumFailuresPerHost: Int,
                                     recoveryPeriod: FiniteDuration,
                                     finishGracefully: Boolean,
                                     processor: Processor[Cmd, Evt],
                                     protocol: BidiFlow[ByteString, Evt, Cmd, ByteString, Any])
                                    (implicit system: ActorSystem, mat: Materializer)
  extends GraphStage[BidiShape[(Command[Cmd], Context), (Try[Event[Evt]], Context), HostEvent, HostEvent]]
{
  val commandIn = Inlet[(Command[Cmd], Context)]("ClientStage.Command.In")
  val eventOut = Outlet[(Try[Event[Evt]], Context)]("ClientStage.Event.Out")

  val connectionEventIn = Inlet[HostEvent]("ClientStage.ConnectionEvent.In")
  val connectionEventOut = Outlet[HostEvent]("ClientStage.ConnectionEvent.Out")

  override def shape = new BidiShape(commandIn, eventOut, connectionEventIn, connectionEventOut)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape) {
    private val hosts               = mutable.Map.empty[Host, Int]
    private val hostFailures        = mutable.Map.empty[Host, Int]
    private val connectionPool      = mutable.Queue.empty[Connection]
    private val failures            = mutable.Queue.empty[(Try[Event[Evt]], Context)]
    private var antennaId           = 0
    private var closingOnCommandIn  = false

    override def preStart(): Unit = {
      pull(connectionEventIn)
      pull(commandIn)
      scheduleWithFixedDelay(Done, FiniteDuration(0, TimeUnit.SECONDS), recoveryPeriod)
    }

    def nextId(): Int = {
      antennaId += 1
      antennaId
    }

    def addHost(host: Host): Unit =
      if (!hosts.contains(host)) {
        hosts += (host -> 0)
        pullCommand(true)
      }

    def ensureConnections(): Unit = {
      hosts
        .find(_._2 < connectionsPerHost)
        .foreach {
          case (host, connectionCount) =>
            val connection = Connection(host, nextId())
            connection.initialize()
            connectionPool.enqueue(connection)
            hosts(connection.host) = connectionCount + 1
        }

      pullCommand(false)
    }

    def pullCommand(shouldInitializeConnection: Boolean): Unit = {
      if (hosts.isEmpty && isAvailable(commandIn)) {
        val (_, context) = grab(commandIn)
        failures.enqueue((Failure(NoConnectionsAvailableException), context))

        if (isAvailable(eventOut) && failures.nonEmpty)
          push(eventOut, failures.dequeue())

        pull(commandIn)
      } else if (isAvailable(commandIn)) {
        connectionPool.dequeueFirst(_.canBePushedForCommand) match {
          case Some(connection) =>
            val (command, context) = grab(commandIn)
            connection.pushCommand(command, context)
            connectionPool.enqueue(connection)
            pull(commandIn)
          case None => if (shouldInitializeConnection) ensureConnections()
        }
      }
    }

    def connectionFailed(connection: Connection, cause: Throwable): Unit = {
      val host = connection.host
      val totalFailure = hostFailures.getOrElse(host, 0) + 1
      hostFailures(host) = totalFailure
      system.log.warning(s"Connection ${connection.connectionId} to $host failed due to ${cause.getMessage}")

      if (hostFailures(host) >= maximumFailuresPerHost) {
        system.log.error(cause, s"Dropping $host, failed $totalFailure times")
        emit(connectionEventOut, HostDown(host))
        removeHost(host, Some(cause))
      } else {
        removeConnection(connection, Some(cause))
      }
    }

    def removeHost(host: Host, cause: Option[Throwable] = None): Unit = {
      hosts.remove(host)
      hostFailures.remove(host)
      connectionPool.dequeueAll(_.host == host).foreach(_.close(cause))

      if (isAvailable(eventOut) && failures.nonEmpty) {
        push(eventOut, failures.dequeue())
      }

      pullCommand(true)
    }

    def removeConnection(connection: Connection, cause: Option[Throwable]): Unit = {
      hosts(connection.host) = hosts(connection.host) - 1
      connectionPool.dequeueAll(_.connectionId == connection.connectionId).foreach(_.close(cause))

      if (isAvailable(eventOut) && failures.nonEmpty) {
        push(eventOut, failures.dequeue())
      }

      pullCommand(true)
    }

    setHandler(connectionEventOut, EagerTerminateOutput)

    setHandler(connectionEventIn, new InHandler {
      override def onPush(): Unit = {
        grab(connectionEventIn) match {
          case HostUp(connection)   => addHost(connection)
          case HostDown(connection) => removeHost(connection)
        }
        pull(connectionEventIn)
      }

      override def onUpstreamFinish(): Unit = {}

      override def onUpstreamFailure(ex: Throwable): Unit =
        failStage(throw new IllegalStateException(s"Stream for ConnectionEvents failed", ex))
    })

    setHandler(commandIn, new InHandler {
      override def onPush(): Unit = pullCommand(shouldInitializeConnection = true)

      override def onUpstreamFinish(): Unit = {
        if (finishGracefully) {
          closingOnCommandIn = true
          connectionPool.foreach(_.requestClose())
        } else {
          connectionPool.foreach(_.close(None))
          completeStage()
        }
      }

      override def onUpstreamFailure(ex: Throwable): Unit =
        failStage(throw new IllegalStateException(s"Requests stream failed", ex))
    })

    setHandler(eventOut, new OutHandler {
      override def onPull(): Unit =
        if (failures.nonEmpty) {
          push(eventOut, failures.dequeue())
        } else {
          connectionPool
            .dequeueFirst(_.canBePulledForEvent)
            .foreach(connection ⇒ {
              if (isAvailable(eventOut)) {
                val event = connection.pullEvent()
                push(eventOut, event)
              }
              connectionPool.enqueue(connection)
            })
        }

      override def onDownstreamFinish(cause: Throwable): Unit = completeStage()
    })

    override def onTimer(timerKey: Any): Unit = hostFailures.clear()

    case class Connection(host: Host, connectionId: Int) {
      connection =>
      private val connectionEventIn     = new SubSinkInlet[Event[Evt]](s"Connection.[$host].[$connectionId].in")
      private val connectionCommandOut  = new SubSourceOutlet[Command[Cmd]](s"Connection.[$host].[$connectionId].out")
      private val contexts = mutable.Queue.empty[Context]
      private var closing = false

      def canBePushedForCommand: Boolean  = connectionCommandOut.isAvailable
      def canBePulledForEvent: Boolean    = connectionEventIn.isAvailable

      def pushCommand(command: Command[Cmd], context: Context): Unit = {
        contexts.enqueue(context)
        connectionCommandOut.push(command)
      }

      def pullEvent(): (Success[Event[Evt]], Context) = {
        val event = connectionEventIn.grab()
        val context = contexts.dequeue()

        if (closing) {
          close(None)
          (Success(event), context)
        } else {
          connectionEventIn.pull()
          (Success(event), context)
        }
      }

      def requestClose(): Unit = {
        closing = true
        if (contexts.isEmpty)
          close(None)
      }

      def close(cause: Option[Throwable]): Unit = {
        val exception = cause match {
          case Some(cause)  => ConnectionClosedWithReasonException(s"Failure to process request to $host at connection $connectionId", cause)
          case None         => ConnectionClosedWithoutReasonException(s"Failure to process request to $host connection $connectionId")
        }

        contexts.dequeueAll(_ ⇒ true).foreach(context ⇒ {
          failures.enqueue((Failure(exception), context))
        })

        connectionEventIn.cancel()
        connectionCommandOut.complete()
      }

      def initialize(): Unit = {
        connectionEventIn.setHandler(new InHandler {
          override def onPush(): Unit = if (isAvailable(eventOut)) push(eventOut, connection.pullEvent())

          override def onUpstreamFinish(): Unit = removeConnection(connection, None)

          override def onUpstreamFailure(reason: Throwable): Unit = reason match {
            case t: TimeoutException ⇒ removeConnection(connection, Some(t))
            case _ ⇒ connectionFailed(connection, reason)
          }
        })

        connectionCommandOut.setHandler(new OutHandler {
          override def onPull(): Unit = pullCommand(shouldInitializeConnection = true)

          override def onDownstreamFinish(cause: Throwable): Unit = {}
        })

        RunnableGraph.fromGraph(GraphDSL.create() { implicit b ⇒
          import GraphDSL.Implicits._

          val pipeline: FlowShape[Command[Cmd], Event[Evt]] =
            b.add(processor
              .flow
              .atop(protocol.reversed)
              .join(Tcp().outgoingConnection(host.host, host.port))
          )

          connectionCommandOut.source ~> pipeline.in
          pipeline.out ~> connectionEventIn.sink

          ClosedShape
        }).run()(subFusingMaterializer)

        connectionEventIn.pull()
      }
    }
  }
}