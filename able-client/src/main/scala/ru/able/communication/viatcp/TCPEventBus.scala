package ru.able.communication.viatcp

import akka.actor.ActorRef
import akka.event.{ActorEventBus, LookupClassification}
import com.typesafe.scalalogging.LazyLogging
import ru.able.communication.viatcp.TCPEventBus.{CommunicationBusFormat, LabelMap, RequestClientUUID}
import ru.able.communication.viatcp.protocol.{LabelMapMessage, MessageFormat, MessageProtocol, SimpleCommand, SingularEvent, Event => EventFormat}

object TCPEventBus {
  val providerName: String = "TCPEventBus"

  trait CommunicationBusFormat
  case object RequestClientUUID extends CommunicationBusFormat
  case class LabelMap(payload: Map[Int, String]) extends CommunicationBusFormat
  case class SubscribeTCPEvent(subscriber: ActorRef, eventClass: TCPEventBus#Classifier) extends CommunicationBusFormat
  case class UnsubscribeTCPEvent(subscriber: ActorRef) extends CommunicationBusFormat
}

class TCPEventBus extends BaseEventBus {
  override type Event = CommunicationBusFormat

  def publish[Evt](event: EventFormat[Evt]): Unit = {
    event match {
      case SingularEvent(SimpleCommand(MessageProtocol.UUID, _))  => publish(RequestClientUUID)
      case SingularEvent(t: LabelMapMessage)                      => publish(LabelMap(t.payload))
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
