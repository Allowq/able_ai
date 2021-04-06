package ru.able.detector.pipeline

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core.{FONT_HERSHEY_PLAIN, LINE_AA, Mat, Point, Scalar}
import org.bytedeco.javacpp.opencv_imgproc.{putText, rectangle}
import akka.util.Timeout

import scala.util.Try
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import ru.able.detector.model.{CanvasFrameSpecial, DetectionOutput, SignedFrame}
import ru.able.util.MediaConversion

class DetectStage(detectorController: ActorRef) extends GraphStage[FlowShape[SignedFrame, SignedFrame]] with LazyLogging
{
  private val in  = Inlet[SignedFrame]("DetectOnFrame.in")
  private val out = Outlet[SignedFrame]("DetectOnFrame.out")

  private val _converter = MediaConversion
  private var _labels: Option[Map[Int, String]] = None

  implicit val askTimeout = Timeout(Duration(15, TimeUnit.SECONDS))

  override def shape: FlowShape[SignedFrame, SignedFrame] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    override def preStart(): Unit = {
      val future = (detectorController ? "getDictionary").mapTo[Map[Int, String]]
      try {
        Await.result(future, askTimeout.duration) match {
          case data: Map[Int, String] => _labels = Some(data)
          case e => logger.warn(s"cannot initialize labelmap for detector. Error: $e", e)
        }
      } catch { case e: Throwable => logger.warn(s"cannot initialize labelmap for detector. Error thrown: $e", e) }
      pull(in)
    }

    override def postStop(): Unit = { }

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        Try {
          val sf = grab[SignedFrame](in)
          processorStraight(sf.UUID, sf.canvasFrameSpecial)
        } recover {
          case e: Exception => { logger.error("Error grabbing the camera frame: ", e) }
        }
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = if (!hasBeenPulled(in)) pull(in)
    })

    private val processorStraight: (UUID, CanvasFrameSpecial) => Unit = {
      (u, cf) => {
        val pic = _converter.horizontal(_converter.toMat(cf.frame))
        val future = (detectorController ? pic).mapTo[DetectionOutput]

        try {
          Await.result(future, askTimeout.duration) match {
            case frame: DetectionOutput => {
              val updatedFrame = _converter.toFrame(drawBoundingBoxes(pic, frame))
              push(out, SignedFrame(u, CanvasFrameSpecial(updatedFrame, cf.date)))
            }
            case e => logger.warn(s"${in.s} pulled with error: $e", e)
          }
        } catch { case e: Throwable => logger.warn(s"${in.s} pulled error thrown: $e", e) }
      }
    }

    private def drawBoundingBoxes(image: Mat, detectionOutput: DetectionOutput): Mat = {
      for (i <- 0 until detectionOutput.boxes.shape.size(1)) {
        val score = detectionOutput.scores(0, i).scalar.asInstanceOf[Float]

        if (score > 0.5) {
          val box = detectionOutput.boxes(0, i).entriesIterator.map(_.asInstanceOf[Float]).toSeq
          // we have to scale the box coordinates to the image size
          val ymin = (box(0) * image.size().height()).toInt
          val xmin = (box(1) * image.size().width()).toInt
          val ymax = (box(2) * image.size().height()).toInt
          val xmax = (box(3) * image.size().width()).toInt

          val label = _labels.getOrElse("unknown") match {
            case string: String => string
            case m: Map[Int, String] => m.getOrElse(i + 1, "unknown")
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