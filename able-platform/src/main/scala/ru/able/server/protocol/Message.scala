package ru.able.server.protocol

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectOutputStream}
import java.util.UUID

import akka.NotUsed
import akka.stream.scaladsl.{BidiFlow, Flow, Framing, Keep}
import akka.util.{ByteString, ByteStringBuilder}
import ru.able.server.model.SocketFrame
import ru.able.util.ObjectInputStreamWithCustomClassLoader

sealed trait MessageFormat {
  def payload: Any
}

// 10
case class FrameSeqMessage(clientUUID: UUID, payload: Seq[SocketFrame]) extends MessageFormat
// 11
case class LabelMapMessage(labelMap: Map[Int, String])

case class SimpleCommand(cmd: Int, payload: String) extends MessageFormat
case class SimpleReply(payload: String) extends MessageFormat
case class SimpleStreamChunk(payload: String) extends MessageFormat
case class SimpleError(payload: String) extends MessageFormat

object SimpleMessage {
  val SOCKET_FRAMES = 1
  val TOTAL_CHUNK_SIZE = 2
  val ECHO = 3
  val CHECK_PING = 99

  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

  val maximumMessageLength: Int = 4 << 20

  val flow = BidiFlow.fromFunctions(serialize, deserialize)

  def fullProtocol = flow.atop(Framing.simpleFramingProtocol(maximumMessageLength))

  def decoderFlow: Flow[ByteString, ByteString, NotUsed] = Framing.simpleFramingProtocolDecoder(maximumMessageLength)
  def deserializeFlow: Flow[ByteString, MessageFormat, NotUsed] = Flow.fromFunction(deserialize)
  def serializeFlow: Flow[MessageFormat, ByteString, NotUsed] = Flow.fromFunction(serialize)
  def encoderFlow: Flow[ByteString, ByteString, NotUsed] = Framing.simpleFramingProtocolEncoder(maximumMessageLength)

  def apply[Cmd, Evt](): BidiFlow[Cmd, ByteString, ByteString, Evt, NotUsed] = {
    BidiFlow
      .fromFunctions(serialize2[Cmd], deserialize2[Evt])
      .atop(Framing.simpleFramingProtocol(maximumMessageLength))
  }

  private def deserialize(bs: ByteString): MessageFormat = {
    val iter = bs.iterator
    iter.getInt match {
      case 1 =>
        SimpleCommand(iter.getInt, new String(iter.toByteString.toArray))
      case 2 =>
        SimpleReply(new String(iter.toByteString.toArray))
      case 3 =>
        SimpleStreamChunk(new String(iter.toByteString.toArray))
      case 4 =>
        SimpleError(new String(iter.toByteString.toArray))
      case 10 =>
        FrameSeqMessage(
          new UUID(iter.getLong, iter.getLong),
          deserializeObject[Seq[SocketFrame]](iter.toByteString)
        )
    }
  }

  private def deserialize2[Cmd](bs: ByteString): Cmd = {
    val iter = bs.iterator
    iter.getInt match {
      case 1 =>
        SimpleCommand(iter.getInt, new String(iter.toByteString.toArray)).asInstanceOf[Cmd]
      case 2 =>
        SimpleReply(new String(iter.toByteString.toArray)).asInstanceOf[Cmd]
      case 3 =>
        SimpleStreamChunk(new String(iter.toByteString.toArray)).asInstanceOf[Cmd]
      case 4 =>
        SimpleError(new String(iter.toByteString.toArray)).asInstanceOf[Cmd]
      case 10 =>
        FrameSeqMessage(
          new UUID(iter.getLong, iter.getLong),
          deserializeObject[Seq[SocketFrame]](iter.toByteString)
        ).asInstanceOf[Cmd]
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
        bsb.putInt(3)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleStreamChunk =>
        bsb.putInt(4)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleError =>
        bsb.putInt(5)
        bsb.putBytes(x.payload.getBytes)
      case x: LabelMapMessage =>
        bsb.putInt(11)
        bsb.putBytes(serializeObject(x.labelMap).toByteArray)
      case _ =>
    }
    bsb.result
  }

  private def serialize2[Evt](m: Evt): ByteString = {
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
      case x: LabelMapMessage =>
        bsb.putInt(11)
        bsb.putBytes(serializeObject(x.labelMap).toByteArray)
      case _ =>
    }
    bsb.result
  }

  private def deserializeObject[T](bytes: ByteString): T = {
    val byteIn = new ByteArrayInputStream(bytes.toArray)
    val objIn = new ObjectInputStreamWithCustomClassLoader(byteIn)
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
