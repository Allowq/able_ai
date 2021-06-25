package ru.able.server.controllers.flow

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Stash}
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import ru.able.server.controllers.flow.CommandActorPublisher.AssignStageActor
import ru.able.server.controllers.flow.protocol.Command

import scala.collection.immutable.Queue
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

object CommandActorPublisher extends LazyLogging {
  case class AssignStageActor(actorRef: ActorRef)

  def getReference(implicit system: ActorSystem, ec: ExecutionContext): Future[ActorRef] =
    system
      .actorSelection("akka://ServerActorSystem/user/CommandPublisherActor")
      .resolveOne()(Timeout(Duration(1, TimeUnit.SECONDS)))
}

class CommandActorPublisher[Cmd] extends Actor with Stash {
  override def receive: Receive = {
    case AssignStageActor(stageActor: ActorRef) =>
      unstashAll()
      context.become(receiveNew(stageActor), false)
    case _: Command[Cmd] => stash()
  }

  def receiveNew(stageActor: ActorRef): Receive = {
    case msg: Command[Cmd] =>
      stageActor ! msg
  }
}

object SourceActorPublisher {
  private def propsActorPublisher: Props = Props(new CommandActorPublisher())

  def apply[Cmd](implicit system: ActorSystem): (ActorRef, SourceActorPublisher[Cmd]) = {
    val actor = system.actorOf(SourceActorPublisher.propsActorPublisher)
    (actor, new SourceActorPublisher(actor))
  }
}

class SourceActorPublisher[Cmd] private (actorPublisher: ActorRef) extends GraphStage[SourceShape[Command[Cmd]]] with LazyLogging {
  private val out: Outlet[Command[Cmd]] = Outlet("CommandPublisher.out")
  override def shape: SourceShape[Command[Cmd]] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      lazy val self: StageActor = getStageActor(onMessage)
      var messages: Queue[Command[Cmd]] = Queue()

      setHandler(out, new OutHandler {
        override def onPull(): Unit = pump()
      })

      private def pump(): Unit = {
        if (isAvailable(out) && messages.nonEmpty) {
          messages.dequeue match {
            case (msg: Command[Cmd], newQueue: Queue[Cmd]) =>
              push(out, msg)
              messages = newQueue
          }
        }
      }

      override def preStart(): Unit = {
        println(s"Actor _${actorPublisher}_ joins us")
        actorPublisher ! AssignStageActor(self.ref)
      }

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

