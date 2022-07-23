package ru.able.service.ObjectDetector

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import ru.able.communication.viatcp.EventBus
import ru.able.communication.viatcp.EventBus.{DeviceRegistered, LabelMap}
import ru.able.communication.viatcp.protocol.{MessageProtocol, SimpleCommand}

object ObjectProcessor {
  val ProviderName: String = "ObjectProcessor"

  def props(bridge: ActorRef, eventBus: EventBus) =
    Props(new ObjectProcessor(bridge, eventBus))
}

final class ObjectProcessor(reactiveBridge: ActorRef, eventBus: EventBus) extends Actor with ActorLogging {

  private var _labelMapOpt: Option[LabelMap] = None

  override def preStart(): Unit = {
    super.preStart()
    eventBus.subscribe(self, EventBus.LabelMap.getClass.getSimpleName)
    eventBus.subscribe(self, EventBus.DeviceRegistered.getClass.getSimpleName)
  }

  override def postStop(): Unit = {
    eventBus.unsubscribe(self)
    super.postStop()
  }

  override def receive: Receive = {
    case _: DeviceRegistered  => reactiveBridge ! SimpleCommand(MessageProtocol.LABEL_MAP, "")
    case lm: LabelMap         => if(_labelMapOpt.isEmpty) _labelMapOpt = Some(lm)
    case msg                  => log.warning(s"ObjectProcessorActor cannot parse incoming request: $msg!")
  }
}
