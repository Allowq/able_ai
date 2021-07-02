package ru.able.services.detector.pipeline

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.{Attributes, FanOutShape2, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core.{FONT_HERSHEY_PLAIN, LINE_AA, Mat, Point, Scalar}
import org.bytedeco.javacpp.opencv_imgproc.{putText, rectangle}
import akka.util.Timeout
import ru.able.server.controllers.flow.model.{FrameSeqMessage, LabelMapMessage, SimpleCommand}
import ru.able.server.controllers.flow.protocol.{MessageProtocol, SingularCommand, SingularEvent}
import ru.able.server.model.SocketFrame

import scala.util.{Failure, Success, Try}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import ru.able.services.detector.model.{CanvasFrameSpecial, DetectionOutput, SignedFrame}
import ru.able.util.MediaConversion

class DetectorStage[Evt, Cmd](detectorController: ActorRef) extends GraphStage[FanOutShape2[Evt, SignedFrame, Cmd]] with LazyLogging
{
  private val in  = Inlet[Evt]("DetectorStage.in")
  private val outFrame = Outlet[SignedFrame]("DetectorStage.outFrame")
  private val outCommand = Outlet[Cmd]("DetectorStage.outCommand")

  private val _mediaConverter = MediaConversion
  private var _labels: Option[Map[Int, String]] = None

  implicit val askTimeout = Timeout(Duration(15, TimeUnit.SECONDS))

  private def convertToFS(t: SocketFrame): CanvasFrameSpecial = CanvasFrameSpecial(t)

  val shape = new FanOutShape2(in, outFrame, outCommand)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    override def preStart(): Unit = {
      // TODO: Upgrade it to concurrent.Try
      Try {
        val future = (detectorController ? "getDictionary").mapTo[Map[Int, String]]
        Await.result(future, askTimeout.duration) match {
          case data: Map[Int, String] => _labels = Some(data)
        }
      } recover { case ex => logger.warn(s"Cannot initialize labelmap for detector. Error thrown: $ex") }

      pull(in)
    }

    override def postStop(): Unit = { }

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        Try {
          val SingularEvent(evt) = grab[Evt](in)

          evt match {
            case FrameSeqMessage(uuid, socketFrames) => {
              socketFrames
                .map(convertToFS)
                .map(SignedFrame(uuid, _))
                .foreach(sf => detectAndPush(sf.UUID, sf.canvasFrameSpecial))
            }
            case SimpleCommand(cmd, _) => {
              if (cmd == MessageProtocol.LABEL_MAP && _labels.isDefined)
                push(outCommand, SingularCommand(LabelMapMessage(_labels.get)).asInstanceOf[Cmd])
              else pull(in)
            }
          }
        } match {
          case Success(_) =>
          case Failure(ex) => logger.error(s"Parsing incoming event failed with exception: $ex"); pull(in)
        }
      }
    })

    setHandler(outFrame, new OutHandler {
      override def onPull(): Unit = if (!hasBeenPulled(in)) pull(in)
    })

    setHandler(outCommand, new OutHandler {
      override def onPull(): Unit = if (!hasBeenPulled(in)) pull(in)
    })

    private val detectAndPush: (UUID, CanvasFrameSpecial) => Unit = {
      (u, cf) => {
        Try {
          val pic = _mediaConverter.horizontal(_mediaConverter.toMat(cf.frame))

          val future = (detectorController ? pic).mapTo[DetectionOutput]
          Await.result(future, askTimeout.duration) match {
            case frame: DetectionOutput => {
              val updatedFrame = _mediaConverter.toFrame(drawBoundingBoxes(pic, frame))
              push(outFrame, SignedFrame(u, CanvasFrameSpecial(updatedFrame, cf.date)))
            }
          }
        } recover { case ex: Throwable => logger.warn(s"Detecting on frame failed with error: $ex") }
      }
    }

    private def drawBoundingBoxes(image: Mat, detectionOutput: DetectionOutput): Mat = {
      for (i: Int <- 0 until detectionOutput.boxes.shape.size(1)) {
        val score = detectionOutput.scores(0, i).scalar.asInstanceOf[Float]

        if (score > 0.5) {
          val box = detectionOutput.boxes(0, i).entriesIterator.map(_.asInstanceOf[Float]).toSeq
          // we have to scale the box coordinates to the image size
          val ymin = (box(0) * image.size().height()).toInt
          val xmin = (box(1) * image.size().width()).toInt
          val ymax = (box(2) * image.size().height()).toInt
          val xmax = (box(3) * image.size().width()).toInt

          val label: String = _labels match {
            case Some(value: Map[Int, String]) => value.getOrElse(i + 1, "unknown")
            case None => "unknown"
          }

          // draw score value
          putText(image,
            f"$label%s ($score%1.2f)", // text
            new Point(xmin + 6, ymin + 38), // text position
            FONT_HERSHEY_PLAIN, // font type
            2.6, // font scale
            new Scalar(0, 0, 0, 0), // text color
            4, // text thickness
            LINE_AA, // line type
            false) // origin is at the top-left corner
          putText(image,
            f"$label%s ($score%1.2f)", // text
            new Point(xmin + 4, ymin + 36), // text position
            FONT_HERSHEY_PLAIN, // font type
            2.6, // font scale
            new Scalar(0, 230, 255, 0), // text color
            4, // text thickness
            LINE_AA, // line type
            false) // origin is at the top-left corner
          // draw bounding box
          rectangle(image,
            new Point(xmin + 1, ymin + 1), // upper left corner
            new Point(xmax + 1, ymax + 1), // lower right corner
            new Scalar(0, 0, 0, 0), // color
            2, // thickness
            0, // lineType
            0) // shift
          rectangle(image,
            new Point(xmin, ymin), // upper left corner
            new Point(xmax, ymax), // lower right corner
            new Scalar(0, 230, 255, 0), // color
            2, // thickness
            0, // lineType
            0) // shift
        }
      }
      image
    }
  }
}