package ru.able.communication.viatcp

import akka.event.{ActorEventBus, LookupClassification}
import com.typesafe.scalalogging.LazyLogging

import ru.able.communication.viatcp.EventBus.{CommunicationBusFormat, LabelMap}
import ru.able.communication.viatcp.protocol.{LabelMapMessage, SingularEvent, Event => EventFormat}

object EventBus {
  val providerName: String = "EventBus"

  trait CommunicationBusFormat
  case class LabelMap(payload: Map[Int, String]) extends CommunicationBusFormat
}

class EventBus extends BaseEventBus {
  override type Event = CommunicationBusFormat

  def publish[Evt](event: EventFormat[Evt]): Unit = {
    event match {
      case SingularEvent(t: LabelMapMessage) => publish(LabelMap(t.payload))
      case _ =>
    }
  }
}

class BaseEventBus extends ActorEventBus with LookupClassification with LazyLogging {
  override type Classifier = String

  override protected def mapSize: Int = 10

  protected def classify(event: Event): Classifier = event.getClass.getSimpleName

  protected def publish(event: Event, subscriber: Subscriber): Unit = subscriber ! event
}
