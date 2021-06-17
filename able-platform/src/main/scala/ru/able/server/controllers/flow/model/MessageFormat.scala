package ru.able.server.controllers.flow.model

import java.util.UUID

import ru.able.server.model.SocketFrame

sealed trait MessageFormat {
  def payload: Any
}

// 1
case class SimpleCommand(cmd: Int, payload: String) extends MessageFormat
// 2
case class SimpleReply(payload: String) extends MessageFormat
// 3
case class SimpleStreamChunk(payload: String) extends MessageFormat
// 4
case class SimpleError(payload: String) extends MessageFormat
// 10
case class FrameSeqMessage(clientUUID: UUID, payload: Seq[SocketFrame]) extends MessageFormat
// 11
case class LabelMapMessage(payload: Map[Int, String]) extends MessageFormat