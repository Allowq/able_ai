package ru.able.server.controllers.flow

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.{ExecutionContext, Future}
import ru.able.server.controllers.flow.protocol.MessageProtocol._
import ru.able.server.controllers.flow.model.{FrameSeqMessage, MessageFormat, SimpleCommand, SimpleError, SimpleReply, SimpleStreamChunk}
import ru.able.server.controllers.flow.protocol.{Action, ConsumerAction, ProducerAction}
import ru.able.server.controllers.flow.model.ResolversFactory.{BasicRT, CustomReplyRT, DeviceActivated, DeviceDefinition, ExtendedRT, FrameSeqRT, ResolverState, ResolverType}

object ResolversFactory {

  abstract class BaseResolver[In] {
    def process: PartialFunction[In, Action]
  }

  def apply[Evt](resolverType: ResolverType)
                (replyProcessor: AnyRef => Action = {_ => ConsumerAction.Ignore})
                (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
  : BaseResolver[Evt] = {
    resolverType match {
      case BasicRT => new BasicResolver
      case CustomReplyRT => new CustomReplyResolver(replyProcessor)
      case ExtendedRT => new ExtendedResolver
      case FrameSeqRT => new FrameSeqResolver
    }
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
        x: SimpleCommand => Future(SimpleReply("ef52e009-9b9c-4dfd-8e98-af33d900d431"))
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
    }
  }

  private class CustomReplyResolver[Evt](replyProcessor: String => Action)
                                        (implicit mat: Materializer, ec: ExecutionContext) extends BaseResolver[Evt] // with Actor with ActorLogging
  {
    private var _type: ResolverState = DeviceDefinition

//    override def receive: Receive = {
//      case DeviceActivated => _type = DeviceActivated
//      case _ => log.warning(s"CustomReplyResolver cannot parse incoming request.")
//    }

    override def process: PartialFunction[Evt, Action] = {
      case SimpleReply(payload) => replyProcessor(payload)
      case x => println("Unhandled: " + x); ConsumerAction.Ignore
    }
  }
}