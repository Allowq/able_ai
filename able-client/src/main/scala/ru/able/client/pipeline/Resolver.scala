package ru.able.client.pipeline

import akka.stream.Materializer
import scala.concurrent.ExecutionContext.Implicits.global

import ru.able.client.protocol.{Action, ConsumerAction, FrameSeqMessage, MessageFormat, ProducerAction, SimpleCommand, SimpleError, SimpleReply, SimpleStreamChunk}

import scala.concurrent.Future

trait Resolver[In] {
  def process(implicit mat: Materializer): PartialFunction[In, Action]
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