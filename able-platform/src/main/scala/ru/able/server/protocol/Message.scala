package ru.able.server.protocol

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectOutputStream}

import akka.stream.scaladsl.{BidiFlow, Framing}
import akka.util.{ByteString, ByteStringBuilder}
import ru.able.server.model.SocketFrame
import ru.able.util.ObjectInputStreamWithCustomClassLoader

sealed trait MessageFormat {
  def payload: Any
}

case class SimpleCommand(cmd: Int, payload: String) extends MessageFormat
case class FrameSeqMessage(payload: Seq[SocketFrame]) extends MessageFormat
case class SimpleReply(payload: String) extends MessageFormat
case class SimpleStreamChunk(payload: String) extends MessageFormat
case class SimpleError(payload: String) extends MessageFormat

object SimpleMessage {
  val SOCKET_FRAMES = 1
  val TOTAL_CHUNK_SIZE = 2
  val ECHO = 3
  val CHECK_PING = 99

  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

  def deserialize(bs: ByteString): MessageFormat = {
    val iter = bs.iterator
    iter.getInt match {
      case 1 =>
        SimpleCommand(iter.getInt, new String(iter.toByteString.toArray))
      case 2 =>
        FrameSeqMessage(deserializeFrameSeqObj(iter.toByteString))
      case 3 =>
        SimpleReply(new String(iter.toByteString.toArray))
      case 4 =>
        SimpleStreamChunk(new String(iter.toByteString.toArray))
      case 5 =>
        SimpleError(new String(iter.toByteString.toArray))
    }
  }

  def serialize(m: MessageFormat): ByteString = {
    val bsb = new ByteStringBuilder()
    m match {
      case x: SimpleCommand =>
        bsb.putInt(1)
        bsb.putInt(x.cmd)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleReply =>
        bsb.putInt(3)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleStreamChunk =>
        bsb.putInt(4)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleError =>
        bsb.putInt(5)
        bsb.putBytes(x.payload.getBytes)
      case _ =>
    }
    bsb.result
  }

  private def deserializeFrameSeqObj(bytes: ByteString): Seq[SocketFrame] = {
    val byteIn = new ByteArrayInputStream(bytes.toArray)
    val objIn = new ObjectInputStreamWithCustomClassLoader(byteIn)
    val obj: Seq[SocketFrame] = objIn.readObject().asInstanceOf[Seq[SocketFrame]]
    byteIn.close()
    objIn.close()
    obj
  }

  val flow = BidiFlow.fromFunctions(serialize, deserialize)

  def protocol = flow.atop(Framing.simpleFramingProtocol(4 << 20))
}