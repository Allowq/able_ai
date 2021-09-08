package ru.able.communication.viatcp.stage

import akka.stream.Materializer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import ru.able.communication.viatcp.protocol.{Action, ConsumerAction, FrameSeqMessage, LabelMapMessage, MessageFormat, ProducerAction, SimpleCommand, SimpleError, SimpleReply, SimpleStreamChunk}
import ru.able.communication.viatcp.protocol.MessageProtocol._

trait Resolver[In] {
  def process(implicit mat: Materializer): PartialFunction[In, Action]
}

object FrameSeqHandler extends Resolver[MessageFormat] {
  def process(implicit mat: Materializer): PartialFunction[MessageFormat, Action] =
  {
    case FrameSeqMessage(uuid, socketFrames) => {
      println(socketFrames.foreach(_.date))
      ProducerAction.Signal { x: SimpleCommand => Future(SimpleReply("That's OK!")) }
    }
    case SimpleStreamChunk(x)               => if (x.length > 0) ConsumerAction.ConsumeStreamChunk else ConsumerAction.EndStream
    case x: SimpleError                     => ConsumerAction.AcceptError
    case x: SimpleReply                     => ConsumerAction.AcceptSignal
    case cmd: SimpleCommand                 => processSimpleCommands(cmd)
    case LabelMapMessage(payload)           => println(s"LabelMap received: $payload"); ConsumerAction.Ignore
    case x                                  => println("Unhandled: " + x); ConsumerAction.Ignore
  }

  private def processSimpleCommands(command: SimpleCommand): Action = {
    command.cmd match {
      case ECHO => println("ECHO: " + command.payload); ConsumerAction.Ignore
      case UUID => ProducerAction.Signal {
        x: SimpleCommand => Future(SimpleReply("1e7c5a66-2d2c-49f9-b3ea-641fbd94bec9"))
      }
//      case UUID => ConsumerAction.AcceptSignal
      case CHECK_PING => println("PING_ACCEPTED: " + command.payload); ConsumerAction.Ignore
    }
  }
}