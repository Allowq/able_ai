package ru.able.client.protocol

import java.io.{ByteArrayOutputStream, ObjectOutputStream}
import java.util.UUID

import akka.stream.Materializer
import akka.stream.scaladsl.{BidiFlow, Framing}
import akka.util.{ByteString, ByteStringBuilder}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import ru.able.client.pipeline.Resolver
import ru.able.communication.SocketFrame

sealed trait MessageFormat {
  def payload: Any
}

case class FrameSeqMessage(clientUUID: UUID, payload: Seq[SocketFrame]) extends MessageFormat
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

  def deserialize(bs: ByteString): MessageFormat = {
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
    }
  }

  def serialize(m: MessageFormat): ByteString = {
    val bsb = new ByteStringBuilder()
    m match {
      case x: SimpleCommand =>
        bsb.putInt(1)
        bsb.putInt(x.cmd)
        bsb.putBytes(x.payload.getBytes)
      case x: FrameSeqMessage =>
        bsb.putInt(2)
        bsb.putLong(x.clientUUID.getMostSignificantBits)
        bsb.putLong(x.clientUUID.getLeastSignificantBits)
        bsb.putBytes(serializeObject(x.payload).toByteArray)
      case x: SimpleReply =>
        bsb.putInt(3)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleStreamChunk ⇒
        bsb.putInt(4)
        bsb.putBytes(x.payload.getBytes)
      case x: SimpleError ⇒
        bsb.putInt(5)
        bsb.putBytes(x.payload.getBytes)
      case _ =>
    }
    bsb.result
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

  val flow = BidiFlow.fromFunctions(serialize, deserialize)

  def protocol = flow.atop(Framing.simpleFramingProtocol(4 << 20))
}

import ru.able.client.protocol.SimpleMessage._

object FrameSeqHandler extends Resolver[MessageFormat] {
  def process(implicit mat: Materializer): PartialFunction[MessageFormat, Action] =
  {
    case FrameSeqMessage(uuid, socketFrames) =>
      println(socketFrames.foreach(_.date))
      ProducerAction.Signal { x: SimpleCommand => Future(SimpleReply("That's OK!")) }
    case SimpleStreamChunk(x)               => if (x.length > 0) ConsumerAction.ConsumeStreamChunk else ConsumerAction.EndStream
    case x: SimpleError                     => ConsumerAction.AcceptError
    case x: SimpleReply                     => ConsumerAction.AcceptSignal
    case SimpleCommand(CHECK_PING, payload) => ProducerAction.Signal { x: SimpleCommand ⇒ Future(SimpleReply("PING_ACCEPTED")) }
    case x                                  => println("Unhandled: " + x); ConsumerAction.Ignore
  }
}