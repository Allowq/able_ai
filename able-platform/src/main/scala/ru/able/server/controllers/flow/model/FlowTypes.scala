package ru.able.server.controllers.flow.model

import akka.Done
import akka.actor.ActorRef
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import scala.concurrent.Future

object FlowTypes {

  sealed trait FlowType
  case object BasicFT extends FlowType
  case object ExtendedFT extends FlowType

  type SimplePublishFlow = Tuple2[ActorRef, Flow[ByteString, ByteString, Future[Done]]]
  type ControlledFlow = Tuple3[ActorRef, ActorRef, Flow[ByteString, ByteString, Future[Done]]]
}