package ru.able.server.controllers.flow

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import com.typesafe.scalalogging.LazyLogging
import ru.able.server.controllers.flow.CommandActorPublisher.AssignStageActor
import ru.able.server.controllers.flow.protocol.Command
import ru.able.server.controllers.flow.stages.CheckoutStage.CheckoutStageCommands

object CommandActorPublisher extends LazyLogging {
  case class AssignStageActor(actorRef: ActorRef)

  def props: Props = Props(new CommandActorPublisher)
}

class CommandActorPublisher[Cmd] extends Actor with Stash with ActorLogging {
  override def receive: Receive = {
    case AssignStageActor(stageActor: ActorRef) =>
      unstashAll()
      context.become(receiveNew(stageActor), false)
    case _: Command[Cmd] => stash()
    case _: CheckoutStageCommands => stash()
    case msg => log.warning(s"CommandActorPublisher cannot parse incoming request: $msg!")
  }

  def receiveNew(stageActor: ActorRef): Receive = {
    case cmdMsg: Command[Cmd] => stageActor ! cmdMsg
    case checkoutCmd: CheckoutStageCommands => stageActor ! checkoutCmd
    case errMsg => log.warning(s"StageActor ${stageActor.path} cannot parse incoming request: $errMsg!")
  }
}

