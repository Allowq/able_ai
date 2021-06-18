package ru.able.server.controllers.flow

import akka.stream.Materializer
import akka.stream.StreamRefMessages.Payload
import akka.stream.scaladsl.{Sink, Source}
import ru.able.server.controllers.flow.model.ResolversFactory.{BasicRT, ExtendedRT, FrameSeqRT, ResolverType}

import scala.concurrent.{ExecutionContext, Future}
import ru.able.server.controllers.flow.protocol.MessageProtocol._
import ru.able.server.controllers.flow.model.{FrameSeqMessage, MessageFormat, SimpleCommand, SimpleError, SimpleReply, SimpleStreamChunk}
import ru.able.server.controllers.flow.protocol.{Action, ConsumerAction, ProducerAction}

object ResolversFactory {

  abstract class BaseResolver[In] {
    def process: PartialFunction[In, Action]
  }

  private class BasicResolver[Evt](implicit mat: Materializer, ec: ExecutionContext) extends BaseResolver[Evt] {
    def process: PartialFunction[Evt, Action] = {
      case SimpleStreamChunk(x) => if (x.length > 0) ConsumerAction.ConsumeStreamChunk else ConsumerAction.EndStream
      case x: SimpleError => ConsumerAction.AcceptError
      case SimpleReply(payload) => println(s"Simple reply: $payload"); ConsumerAction.AcceptSignal
      case cmd: SimpleCommand => processSimpleCommands(cmd)
      case x => println("Unhandled: " + x); ConsumerAction.Ignore
    }

    private def processSimpleCommands(command: SimpleCommand): Action = command.cmd match {
      case ECHO => ProducerAction.Signal {
        x: SimpleCommand => Future(SimpleReply(command.payload))
      }
      case UUID => ProducerAction.Signal {
        x: SimpleCommand => Future(SimpleReply("1e7c5a66-2d2c-49f9-b3ea-641fbd94bec9"))
      }
      case CHECK_PING => ProducerAction.Signal {
        x: SimpleCommand => Future(SimpleReply("PONG"))
      }
    }
  }

  private class ExtendedResolver[Evt](implicit mat: Materializer, ec: ExecutionContext) extends BaseResolver[Evt] {
    def process: PartialFunction[Evt, Action] = {
      case SimpleStreamChunk(x) =>
        if (x.length > 0) ConsumerAction.ConsumeStreamChunk else ConsumerAction.EndStream
      case SimpleCommand(CHECK_PING, payload) =>
        ProducerAction.Signal { x: SimpleCommand => Future(SimpleReply("PING_ACCEPTED")) }
      case SimpleCommand(TOTAL_CHUNK_SIZE, payload) => {
        ProducerAction.ConsumeStream { x: Source[SimpleStreamChunk, Any] =>
          x
            .runWith(Sink.fold[Int, MessageFormat](0) { (b, a) => b + a.payload.asInstanceOf[String].length })
            .map(x ⇒ SimpleReply(x.toString))
        }
      }
      case SimpleCommand(ECHO, payload) =>
        ProducerAction.Signal { x: SimpleCommand ⇒ Future(SimpleReply(x.payload)) }
      case x => println("Unhandled: " + x); ConsumerAction.Ignore
    }
  }

  private class FrameSeqResolver[Evt](implicit mat: Materializer, ec: ExecutionContext) extends BaseResolver[Evt] {
    def process: PartialFunction[Evt, Action] = {
      case FrameSeqMessage(uuid, socketFrames) =>
        socketFrames.foreach(sf => println(s"${uuid.toString}: ${sf.date.toString}"))
        ConsumerAction.AcceptSignal
      //      ProducerAction.Signal { x: SimpleCommand => Future(SimpleReply("PING_ACCEPTED")) }
    }
  }

  def apply[Evt](resolverType: ResolverType)(implicit mat: Materializer, ec: ExecutionContext): BaseResolver[Evt] = {
    resolverType match {
      case BasicRT => new BasicResolver
      case ExtendedRT => new ExtendedResolver
      case FrameSeqRT => new FrameSeqResolver
    }
  }
}