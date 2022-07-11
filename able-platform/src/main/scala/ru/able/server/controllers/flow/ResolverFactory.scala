package ru.able.server.controllers.flow

import akka.actor.ActorContext
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.{ExecutionContext, Future}
import ru.able.server.controllers.flow.protocol.MessageProtocol._
import ru.able.server.controllers.flow.model.{FrameSeqMessage, MessageFormat, SimpleCommand, SimpleError, SimpleReply, SimpleStreamChunk}
import ru.able.server.controllers.flow.protocol.{Action, ConsumerAction, ProducerAction}
import ru.able.server.controllers.flow.model.ResolversFactory.{BasicRT, ExtendedRT, ResolverType}

object ResolverFactory {

  abstract class BaseResolver[In] {
    def process: PartialFunction[In, Action]
  }

  def apply[Evt](resolverType: ResolverType)
                (implicit context: ActorContext, mat: Materializer, ec: ExecutionContext)
  : BaseResolver[Evt] =
  {
    resolverType match {
      case BasicRT => new BasicResolver
      case ExtendedRT => new ExtendedResolver
    }
  }

  private def processSimpleCommand(command: SimpleCommand)(implicit ec: ExecutionContext, mat: Materializer): Action = {
    command.cmd match {
      case ECHO => ProducerAction.Signal { _: SimpleCommand => Future(SimpleReply(command.payload)) }
//      case UUID => ProducerAction.Signal { _: SimpleCommand => Future(SimpleReply("ef52e009-9b9c-4dfd-8e98-af33d900d431")) }
      case CHECK_PING => ProducerAction.Signal { _: SimpleCommand => Future(SimpleReply("PONG")) }
      case TOTAL_CHUNK_SIZE => ProducerAction.ConsumeStream { x: Source[SimpleStreamChunk, Any] =>
        x
          .runWith(Sink.fold[Int, MessageFormat](0) { (b, a) => b + a.payload.asInstanceOf[String].length })
          .map(x ⇒ SimpleReply(x.toString))
      }
      case LABEL_MAP => ConsumerAction.AcceptSignal
      case _ => ConsumerAction.Ignore
    }
  }

  private class BasicResolver[Evt](implicit mat: Materializer, ec: ExecutionContext) extends BaseResolver[Evt] {
    def process: PartialFunction[Evt, Action] = {
      case SimpleStreamChunk(x) => if (x.length > 0) ConsumerAction.ConsumeStreamChunk else ConsumerAction.EndStream
      case cmd: SimpleCommand => processSimpleCommand(cmd)
      case SimpleReply(payload) => println(s"Simple reply: $payload"); ConsumerAction.AcceptSignal
      case _: SimpleError => ConsumerAction.AcceptError
      case x => println("Unhandled: " + x); ConsumerAction.Ignore
    }
  }

  private class ExtendedResolver[Evt](implicit mat: Materializer, ec: ExecutionContext) extends BaseResolver[Evt] {
    def process: PartialFunction[Evt, Action] = {
      case SimpleStreamChunk(x) => if (x.length > 0) ConsumerAction.ConsumeStreamChunk else ConsumerAction.EndStream
      case cmd: SimpleCommand => processSimpleCommand(cmd)
      case _: FrameSeqMessage => ConsumerAction.AcceptSignal
      case _: SimpleReply => ConsumerAction.Ignore
      case _: SimpleError => ConsumerAction.AcceptError
      case x => println("Unhandled: " + x); ConsumerAction.Ignore
    }
  }
}