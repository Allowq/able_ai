package ru.able.services.twin.model

import java.util.UUID

import akka.actor.ActorRef

import ru.able.server.controllers.flow.model.FlowController.FlowType
import ru.able.services.session.model.SessionController.SessionID

object DeviceTwinController {

  case class TwinID(uuid: UUID)

  sealed trait BaseTwin {
    def sessionId: SessionID
  }
  case class DeviceTwin(sessionId: SessionID,
                        flowType: FlowType,
                        commandPublisher: Option[ActorRef]) extends BaseTwin

}
