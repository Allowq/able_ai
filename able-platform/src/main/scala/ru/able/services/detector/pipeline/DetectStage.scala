package ru.able.services.detector.pipeline

import java.util.{Timer, UUID}
import java.util.concurrent.{TimeUnit, TimeoutException}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.{Attributes, FanOutShape2, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core.{FONT_HERSHEY_PLAIN, LINE_AA, Mat, Point, Scalar}
import org.bytedeco.javacpp.opencv_imgproc.{putText, rectangle}
import akka.util.Timeout
import ru.able.server.controllers.flow.model.{FrameSeqMessage, LabelMapMessage, SimpleCommand}
import ru.able.server.controllers.flow.protocol.{Event, MessageProtocol, SingularCommand, SingularEvent}
import ru.able.server.model.SocketFrame

import scala.util.{Failure, Success, Try}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import ru.able.services.detector.model.{CanvasFrameSpecial, DetectionOutput, SignedFrame}
import ru.able.util.{Helpers, MediaConversion}

class DetectorStage[Evt, Cmd](detectorController: ActorRef) extends GraphStage[FanOutShape2[Event[Evt], SignedFrame, Cmd]] with LazyLogging
{
  implicit val _askTimeout = Timeout(Duration(10, TimeUnit.SECONDS))

  private val in  = Inlet[Event[Evt]]("DetectorStage.in")
  private val outFrame = Outlet[SignedFrame]("DetectorStage.outFrame")
  private val outCommand = Outlet[Cmd]("DetectorStage.outCommand")

  private val _timer = new Timer
  private var _labels: Option[Map[Int, String]] = None
  private var _pending: Option[Either[Seq[SignedFrame], SimpleCommand]] = None

  private def convertToFS(t: SocketFrame): CanvasFrameSpecial = CanvasFrameSpecial(t)

  val shape = new FanOutShape2(in, outFrame, outCommand)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    override def preStart(): Unit = initializeLabelMap

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        Try {
          val SingularEvent(evt) = grab(in)

          evt match {
            case FrameSeqMessage(uuid, socketFrames) => {
              _pending = Some(Left(
                socketFrames
                  .map(convertToFS)
                  .map(SignedFrame(uuid, _))))
            }
            case cmd: SimpleCommand => _pending = Some(Right(cmd))
          }

          _pending.get match {
            case Left(frameSeq) if isAvailable(outFrame) => checkFramesPendingAndPush(frameSeq)
            case Right(command) if isAvailable(outCommand) => decodeCommandAndPush(command)
            case _ =>
          }
        } match {
          case Success(_) =>
          case Failure(ex) => logger.error(s"Parsing incoming event failed with exception: $ex")
        }
      }

      override def onUpstreamFinish(): Unit = if (_pending.isEmpty) completeStage()
    })

    setHandler(outFrame, new OutHandler {
      override def onPull(): Unit = {
        if (_pending.isDefined)
          _pending.get.left.foreach(checkFramesPendingAndPush)
        else if (!hasBeenPulled(in))
          pull(in)
      }
    })

    setHandler(outCommand, new OutHandler {
      override def onPull(): Unit = {
        if (_pending.isDefined)
          _pending.get.right.foreach(decodeCommandAndPush)
        else if (!hasBeenPulled(in))
          pull(in)
      }
    })

    private def decodeFrameAndPush: (UUID, CanvasFrameSpecial) => Unit = { (uuid, cfs) =>
      if (_labels.isEmpty) {
        push(outFrame, SignedFrame(uuid, cfs))
      } else {
        Try {
          val pic = MediaConversion.horizontal(MediaConversion.toMat(cfs.frame))

          val future = (detectorController ? pic).mapTo[DetectionOutput]
          Await.result(future, _askTimeout.duration) match {
            case frame: DetectionOutput => {
              val updatedFrame = MediaConversion.toFrame(drawBoundingBoxes(pic, frame))
              push(outFrame, SignedFrame(uuid, CanvasFrameSpecial(updatedFrame, cfs.date)))
            }
          }
        } recover {
          case ex: Throwable => logger.warn(s"Detecting on frame failed with error: $ex")
        }
      }
    }

    private def checkFramesPendingAndPush(seq: Seq[SignedFrame]): Unit = {
      seq.headOption match {
        case Some(value) => {
          decodeFrameAndPush(value.UUID, value.canvasFrameSpecial)
          seq match {
            case Seq(_) => _pending = None
            case Seq(_, tail@_*)  => _pending = Some(Left(tail))
          }
        }
        case None => _pending = None
      }
      if (_pending.isEmpty) {
        if (isClosed(in))
          completeStage()
        else if (isAvailable(outCommand))
          pull(in)
      }
    }

    private def decodeCommandAndPush: SimpleCommand => Unit = { sc =>
      try {
        if (sc.cmd == MessageProtocol.LABEL_MAP && _labels.isDefined)
          push(outCommand, SingularCommand(LabelMapMessage(_labels.get)).asInstanceOf[Cmd])
        _pending = None
      } catch { case ex: Throwable => logger.warn(s"Detecting on frame failed with error: $ex") }

      if (isClosed(in))
        completeStage()
      else if (isAvailable(outFrame))
        pull(in)
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
            case Some(value: Map[Int, String]) => value.getOrElse(
              detectionOutput.classes(0, i).scalar.asInstanceOf[Float].toInt,
              "unknown")
            case None => "unknown"
          }

          // draw score value
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

    private def initializeLabelMap: Unit = {
      Try {
        val future = (detectorController ? "getDictionary").mapTo[Map[Int, String]]
        Await.result(future, _askTimeout.duration) match {
          case data: Map[Int, String] => _labels = Some(data)
        }
      } recover {
        case _: TimeoutException => {
          logger.warn(s"Request of LabelMap to DetectorController was timed out. Request will be repeated later.")
          Helpers.runAfterDelay(_askTimeout, _timer)(() => initializeLabelMap)
        }
        case ex => logger.warn(s"Cannot initialize labelmap for detector. Error thrown: $ex")
      }
    }
  }
}