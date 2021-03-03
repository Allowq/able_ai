package ru.able.server.pipeline

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.ExecutionContext.Implicits.global
import ru.able.server.protocol.{Action, ConsumerAction, FrameSeqMessage, MessageFormat, ProducerAction, SimpleCommand, SimpleError, SimpleMessage, SimpleReply, SimpleStreamChunk}
import ru.able.server.protocol.SimpleMessage._

import scala.concurrent.Future

trait Resolver[In] {
  def process(implicit mat: Materializer): PartialFunction[In, Action]
}

object SimpleHandler extends Resolver[MessageFormat] {
  def process(implicit mat: Materializer): PartialFunction[MessageFormat, Action] = {
    case SimpleStreamChunk(x)               => if (x.length > 0) ConsumerAction.ConsumeStreamChunk else ConsumerAction.EndStream
    case x: SimpleError                     => ConsumerAction.AcceptError
    case x: SimpleReply                     => ConsumerAction.AcceptSignal
    case SimpleCommand(CHECK_PING, payload) => ProducerAction.Signal { x: SimpleCommand ⇒ Future(SimpleReply("PONG")) }
    case x                                  => println("Unhandled: " + x); ConsumerAction.Ignore
  }
}

object FrameSeqHandler extends Resolver[MessageFormat] {
  def process(implicit mat: Materializer): PartialFunction[MessageFormat, Action] =
  {
    case FrameSeqMessage(uuid, socketFrames) =>
      socketFrames.foreach(sf => println(s"${uuid.toString}: ${sf.date.toString}"))
      ConsumerAction.AcceptSignal
//      ProducerAction.Signal { x: SimpleCommand => Future(SimpleReply("PING_ACCEPTED")) }
    case SimpleStreamChunk(x) =>
      if (x.length > 0) ConsumerAction.ConsumeStreamChunk else ConsumerAction.EndStream
    case SimpleCommand(CHECK_PING, payload) =>
      ProducerAction.Signal { x: SimpleCommand => Future(SimpleReply("PING_ACCEPTED")) }
    case SimpleCommand(TOTAL_CHUNK_SIZE, payload) =>
      ProducerAction.ConsumeStream { x: Source[SimpleStreamChunk, Any] => x
        .runWith(Sink.fold[Int, MessageFormat](0) { (b, a) => b + a.payload.asInstanceOf[String].length })
        .map(x ⇒ SimpleReply(x.toString))
      }
    case SimpleCommand(ECHO, payload) =>
      ProducerAction.Signal { x: SimpleCommand ⇒ Future(SimpleReply(x.payload)) }
    case x => println("Unhandled: " + x); ConsumerAction.Ignore
  }
}