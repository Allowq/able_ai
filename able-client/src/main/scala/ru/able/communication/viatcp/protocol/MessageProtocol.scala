package ru.able.communication.viatcp.protocol

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.util.UUID

import akka.stream.scaladsl.{BidiFlow, Framing}
import akka.util.{ByteString, ByteStringBuilder}
import ru.able.communication.viasocket.SocketFrame

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

object MessageProtocol {
  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

  val SOCKET_FRAMES = 1
  val TOTAL_CHUNK_SIZE = 2
  val ECHO = 3
  val UUID = 4
  val LABEL_MAP = 5
  val CHECK_PING = 99

  val maximumMessageLength: Int = 4 << 20

  val flow = BidiFlow.fromFunctions(serialize, deserialize)

  def protocol = flow.atop(Framing.simpleFramingProtocol(maximumMessageLength))

  private def deserialize(bs: ByteString): MessageFormat = {
    val iter = bs.iterator
    iter.getInt match {
      case 1 =>
        SimpleCommand(iter.getInt, new String(iter.toByteString.toArray))
      case 3 =>
        SimpleReply(new String(iter.toByteString.toArray))
      case 4 =>
        SimpleStreamChunk(new String(iter.toByteString.toArray))
      case 5 =>
        SimpleError(new String(iter.toByteString.toArray))
      case 11 =>
        LabelMapMessage(deserializeObject[Map[Int, String]](iter.toByteString))
    }
  }

  private def serialize(m: MessageFormat): ByteString = {
    val bsb = new ByteStringBuilder()
    m match {
      case x: SimpleCommand =>
        bsb.putInt(1)
        bsb.putInt(x.cmd)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleReply =>
        bsb.putInt(2)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleStreamChunk ⇒
        bsb.putInt(3)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleError ⇒
        bsb.putInt(4)
        bsb.putBytes(x.payload.getBytes)
      case x: FrameSeqMessage =>
        bsb.putInt(10)
        bsb.putLong(x.clientUUID.getMostSignificantBits)
        bsb.putLong(x.clientUUID.getLeastSignificantBits)
        bsb.putBytes(serializeObject(x.payload).toByteArray)
      case _ =>
    }
    bsb.result
  }

  private def deserializeObject[T](bytes: ByteString): T = {
    val byteIn = new ByteArrayInputStream(bytes.toArray)
    val objIn = new ObjectInputStream(byteIn)
    val obj: T = objIn.readObject().asInstanceOf[T]
    byteIn.close()
    objIn.close()
    obj
  }

  private def serializeObject(value: Any): ByteArrayOutputStream = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
    val out = new ObjectOutputStream(stream)
    out.writeObject(value)
    out.writeBytes("\n")
    out.flush()
    out.close()
    stream
  }
}