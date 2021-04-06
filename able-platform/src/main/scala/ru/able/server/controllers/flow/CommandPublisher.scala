package ru.able.server.controllers.flow

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Stash}
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import ru.able.server.controllers.flow.CommandActorPublisher.AssignStageActor
import ru.able.server.protocol.Command

import scala.collection.immutable.Queue

object CommandActorPublisher {
  case class AssignStageActor(actorRef: ActorRef)

  def props = Props(new CommandActorPublisher)
}

class CommandActorPublisher[Cmd] extends Actor with Stash {
  override def receive: Receive = {
    case _: Command[Cmd] => stash()
    case AssignStageActor(stageActor: ActorRef) =>
      unstashAll()
      context.become(receiveNew(stageActor))
  }

  def receiveNew(stageActor: ActorRef): Receive = {
    case msg: String =>
      stageActor ! msg
  }
}

object SourceActorPublisher {
  def apply[Cmd](system: ActorSystem): SourceActorPublisher[Cmd] =
    new SourceActorPublisher(system.actorOf(CommandActorPublisher.props, "ActorPublisher"))
}

class SourceActorPublisher[Cmd] private (actorPublisher: ActorRef) extends GraphStage[SourceShape[Cmd]] {
  private val out: Outlet[Cmd] = Outlet("CommandPublisher.out")
  override def shape: SourceShape[Cmd] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      lazy val self: StageActor = getStageActor(onMessage)
      var messages: Queue[Cmd] = Queue()

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pump()
        }
      })

      private def pump(): Unit = {
        if (isAvailable(out) && messages.nonEmpty) {
          messages.dequeue match {
            case (msg: Cmd, newQueue: Queue[Cmd]) =>
              push(out, msg)
              messages = newQueue
          }
        }
      }

      override def preStart(): Unit = actorPublisher ! AssignStageActor(self.ref)

      private def onMessage(x: (ActorRef, Any)): Unit = {
        x match {
          case (_, msg: Cmd) =>
            messages = messages.enqueue(msg)
            pump()
        }
      }
    }
}

