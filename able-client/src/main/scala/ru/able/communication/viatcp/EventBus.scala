package ru.able.communication.viatcp

import akka.actor.ActorRef
import akka.event.{ActorEventBus, LookupClassification}
import com.typesafe.scalalogging.LazyLogging
import ru.able.communication.viatcp.EventBus.{DeviceRegistered, EventBusMessageFormat, LabelMap}
import ru.able.communication.viatcp.protocol.{LabelMapMessage, MessageProtocol, SimpleCommand, SingularEvent, Event => EventFormat}

object EventBus {
  val providerName: String = "EventBus"

  trait EventBusMessageFormat
  case class LabelMap(payload: Map[Int, String]) extends EventBusMessageFormat
  case class DeviceRegistered(payload: String) extends EventBusMessageFormat
}

class EventBus extends BaseEventBus {
  override type Event = EventBusMessageFormat

  def publish[Evt](event: EventFormat[Evt]): Unit = {
    event match {
      case SingularEvent(SimpleCommand(MessageProtocol.REGISTRATION_SUCCESS, payload)) =>
        publish(DeviceRegistered(payload))
      case SingularEvent(LabelMapMessage(payload)) =>
        publish(LabelMap(payload))
      case errMsg =>
        logger.warn(s"EventBus cannot parse incoming message: $errMsg!")
    }
  }

  override def subscribe(subscriber: ActorRef, to: String): Boolean =
    super.subscribe(subscriber, to.split("\\$").last)
}

class BaseEventBus extends ActorEventBus with LookupClassification with LazyLogging {
  override type Classifier = String

  override protected def mapSize: Int = 10

  protected def classify(event: Event): Classifier = event.getClass.getSimpleName

  protected def publish(event: Event, subscriber: Subscriber): Unit = subscriber ! event
}
