package ru.able.server.controllers.flow.model

object FlowController {

  sealed trait FlowType

  case object BasicFT extends FlowType

  case object DetectionFT extends FlowType

  case object ManagedDetectionFT extends FlowType

}