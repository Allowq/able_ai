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
      ProducerAction.Signal { _: SimpleCommand => Future(SimpleReply("That's OK!")) }
    }
    case cmd: SimpleCommand   => processSimpleCommands(cmd)
    case SimpleStreamChunk(x) => if (x.length > 0) ConsumerAction.ConsumeStreamChunk else ConsumerAction.EndStream
    case _: SimpleReply       => ConsumerAction.AcceptSignal
    case _: LabelMapMessage   => ConsumerAction.AcceptSignal
    case _: SimpleError       => ConsumerAction.AcceptError
    case x                    => println("Unhandled: " + x); ConsumerAction.Ignore
  }

  private def processSimpleCommands(command: SimpleCommand): Action = {
    command.cmd match {
      case ECHO => println("ECHO: " + command.payload); ConsumerAction.Ignore
      case UUID => ConsumerAction.AcceptSignal
      case CHECK_PING => println("PING_ACCEPTED: " + command.payload); ConsumerAction.Ignore
      case REGISTRATION_SUCCESS => ConsumerAction.AcceptSignal
      case _ => ConsumerAction.Ignore
    }
  }
}