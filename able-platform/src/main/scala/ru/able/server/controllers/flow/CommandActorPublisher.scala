package ru.able.server.controllers.flow

import java.util.concurrent.TimeUnit
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Stash}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

import ru.able.server.controllers.flow.CommandActorPublisher.AssignStageActor
import ru.able.server.controllers.flow.protocol.Command

object CommandActorPublisher extends LazyLogging {
  case class AssignStageActor(actorRef: ActorRef)

  def getReference(implicit system: ActorSystem, ec: ExecutionContext): Future[ActorRef] =
    system
      .actorSelection("akka://ServerActorSystem/user/CommandPublisherActor")
      .resolveOne()(Timeout(Duration(1, TimeUnit.SECONDS)))
}

class CommandActorPublisher[Cmd] extends Actor with Stash with ActorLogging {
  override def receive: Receive = {
    case AssignStageActor(stageActor: ActorRef) =>
      unstashAll()
      context.become(receiveNew(stageActor), false)
    case _: Command[Cmd] => stash()
    case msg => log.warning(s"CommandActorPublisher cannot parse incoming request: $msg!")
  }

  def receiveNew(stageActor: ActorRef): Receive = { case msg: Command[Cmd] => stageActor ! msg }
}

