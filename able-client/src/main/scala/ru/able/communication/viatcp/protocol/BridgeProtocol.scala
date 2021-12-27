package ru.able.communication.viatcp.protocol

import akka.actor.ActorRef
import ru.able.communication.viatcp.EventBus

trait BridgeProtocol

case class SubscribeOnEvents(subscriber: ActorRef, eventClass: EventBus#Classifier) extends BridgeProtocol
case class UnsubscribeFromEvents(subscriber: ActorRef) extends BridgeProtocol
