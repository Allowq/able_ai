package ru.able.server.controllers.flow.stages

import java.net.InetSocketAddress

import akka.actor.{ActorContext, ActorRef}
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import com.typesafe.scalalogging.LazyLogging
import ru.able.server.controllers.flow.CommandActorPublisher
import ru.able.server.controllers.flow.CommandActorPublisher.AssignStageActor
import ru.able.server.controllers.flow.protocol.Command

import scala.collection.immutable.Queue

object SourceFromActorStage {

  def apply[Cmd](rAddrOpt: Option[InetSocketAddress])(implicit context: ActorContext)
  : (ActorRef, SourceFromActorStage[Cmd]) =
  {
    val actor = rAddrOpt match {
      case Some(rAddr) => {
        val actorName = rAddr.toString.replaceAll("[/]", "_")
        context.actorOf(CommandActorPublisher.props, s"SourceStagePublisher$actorName")
      }
      case None => context.actorOf(CommandActorPublisher.props)
    }

    (actor, new SourceFromActorStage(actor))
  }
}

class SourceFromActorStage[Cmd] private (actorPublisher: ActorRef) extends GraphStage[SourceShape[Command[Cmd]]] with LazyLogging {
  private val out: Outlet[Command[Cmd]] = Outlet("CommandPublisher.out")
  override def shape: SourceShape[Command[Cmd]] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    lazy val self: StageActor = getStageActor(onMessage)
    var messages: Queue[Command[Cmd]] = Queue()

    setHandler(out, new OutHandler { override def onPull(): Unit = pump() })

    private def pump(): Unit = {
      if (isAvailable(out) && messages.nonEmpty) {
        messages.dequeue match {
          case (msg: Command[Cmd], newQueue: Queue[Cmd]) =>
            push(out, msg)
            messages = newQueue
        }
      }
    }

    override def preStart(): Unit = actorPublisher ! AssignStageActor(self.ref)

    private def onMessage(x: (ActorRef, Any)): Unit = {
      x match {
        case (_, msg: Command[Cmd]) => {
          messages = messages.enqueue(msg)
          pump()
        }
        // TODO: Repair it
        case (actorRef, msg) => {
          logger.warn(s"Cannot process message: $msg from $actorRef!")
        }
      }
    }
  }
}