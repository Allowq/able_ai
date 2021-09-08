package ru.able.server.controllers.flow.protocol

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectOutputStream}
import java.util.UUID

import akka.NotUsed
import akka.stream.scaladsl.{BidiFlow, Flow, Framing}
import akka.util.{ByteString, ByteStringBuilder}
import ru.able.server.controllers.flow.model._
import ru.able.server.model.SocketFrame
import ru.able.util.ObjectInputStreamWithCustomClassLoader

object MessageProtocol {

  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

  val SOCKET_FRAMES = 1
  val TOTAL_CHUNK_SIZE = 2
  val ECHO = 3
  val UUID = 4
  val LABEL_MAP = 5
  val CHECK_PING = 99

  val maximumMessageLength: Int = 4 << 20

  def apply[Cmd, Evt](): BidiFlow[Cmd, ByteString, ByteString, Evt, NotUsed] = {
    BidiFlow
      .fromFunctions(serialize[Cmd], deserialize[Evt])
      .atop(Framing.simpleFramingProtocol(maximumMessageLength))
  }

  def decoderFlow: Flow[ByteString, ByteString, NotUsed] = Framing.simpleFramingProtocolDecoder(maximumMessageLength)
  def deserializeFlow[Evt]: Flow[ByteString, Evt, NotUsed] = Flow.fromFunction(deserialize[Evt])
  def serializeFlow[Cmd]: Flow[Cmd, ByteString, NotUsed] = Flow.fromFunction(serialize[Cmd])
  def encoderFlow: Flow[ByteString, ByteString, NotUsed] = Framing.simpleFramingProtocolEncoder(maximumMessageLength)

  private def deserialize[Cmd](bs: ByteString): Cmd = {
    val iter = bs.iterator
    val command = iter.getInt match {
      case 1 => SimpleCommand(iter.getInt, new String(iter.toByteString.toArray))
      case 2 => SimpleReply(new String(iter.toByteString.toArray))
      case 3 => SimpleStreamChunk(new String(iter.toByteString.toArray))
      case 4 => SimpleError(new String(iter.toByteString.toArray))
      case 10 => FrameSeqMessage(
        new UUID(iter.getLong, iter.getLong),
        deserializeObject[Seq[SocketFrame]](iter.toByteString)
      )
    }

    command.asInstanceOf[Cmd]
  }

  private def serialize[Evt](m: Evt): ByteString = {
    val bsb = new ByteStringBuilder()
    m match {
      case x: SimpleCommand =>
        bsb.putInt(1)
        bsb.putInt(x.cmd)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleReply =>
        bsb.putInt(2)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleStreamChunk =>
        bsb.putInt(3)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleError =>
        bsb.putInt(4)
        bsb.putBytes(x.payload.getBytes)
      case x: LabelMapMessage =>
        bsb.putInt(11)
        bsb.putBytes(serializeObject(x.payload).toByteArray)
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
