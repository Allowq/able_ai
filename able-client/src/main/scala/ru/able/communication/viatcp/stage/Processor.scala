package ru.able.communication.viatcp.stage

import akka.stream.BidiShape
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, Merge, Sink}
import ru.able.communication.viatcp.protocol.{Command, Event, ProducerAction, SingularCommand, SingularEvent, StreamEvent, StreamingCommand}

import scala.concurrent.ExecutionContext

case class Processor[Cmd, Evt](flow: BidiFlow[Command[Cmd], Cmd, Evt, Event[Evt], Any])

object Processor {
  def apply[Cmd, Evt](resolver: Resolver[Evt], producerParallism: Int, shouldReact: Boolean = true)
                     (implicit ec: ExecutionContext): Processor[Cmd, Evt] = {

    val consumerStage = new ConsumerStage[Evt, Cmd](resolver)
    val producerStage = new ProducerStage[Evt, Cmd]()

    val functionApply = Flow[(Event[Evt], ProducerAction[Evt, Cmd])].mapAsync[Command[Cmd]](producerParallism) {
      case (SingularEvent(evt), x: ProducerAction.Signal[Evt, Cmd])        ⇒ x.f(evt).map(SingularCommand[Cmd])
      case (SingularEvent(evt), x: ProducerAction.ProduceStream[Evt, Cmd]) ⇒ x.f(evt).map(StreamingCommand[Cmd])
      case (StreamEvent(evt), x: ProducerAction.ConsumeStream[Evt, Cmd])   ⇒ x.f(evt).map(SingularCommand[Cmd])
      case (StreamEvent(evt), x: ProducerAction.ProcessStream[Evt, Cmd])   ⇒ x.f(evt).map(StreamingCommand[Cmd])
    }

    Processor(BidiFlow.fromGraph[Command[Cmd], Cmd, Evt, Event[Evt], Any] {
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

        val producer = b add producerStage
        val consumer = b add consumerStage

        val commandIn = b add Flow[Command[Cmd]]

        if (shouldReact) {
          val fa = b add functionApply
          val merge = b add Merge[Command[Cmd]](2)
          commandIn           ~> merge.in(0)
          consumer.out0 ~> fa ~> merge.in(1)
                                 merge.out ~> producer
        } else {
          consumer.out0 ~> Sink.ignore
          commandIn ~> producer
        }

        BidiShape(commandIn.in, producer.out, consumer.in, consumer.out1)
      }
    })
  }
}
