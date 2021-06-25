package ru.able.server.controllers.flow.model

object FlowTypes {

  sealed trait FlowType
  case object BasicFT extends FlowType
  case object CustomReplyFT extends FlowType
  case object DetectionFT extends FlowType
  case object ManagedDetectionFT extends FlowType
}