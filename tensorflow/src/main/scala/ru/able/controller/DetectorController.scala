package ru.able.controller

import ru.able.model.{DetectionOutput, DetectorModel, DetectorViaFileDescription}
import java.nio.ByteBuffer

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Source
import akka.stream.{Attributes, Graph, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import org.bytedeco.javacpp.opencv_imgcodecs.imread
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_imgproc.{COLOR_BGR2RGB, cvtColor}
import org.bytedeco.javacv.FrameGrabber.ImageMode
import org.bytedeco.javacv.{FFmpegFrameGrabber, Frame, FrameGrabber, OpenCVFrameGrabber}
import org.platanios.tensorflow.api.{Shape, UINT8}
import org.platanios.tensorflow.api.Tensor

final class DetectorController private (private val _detectorModel: DetectorModel)
{
  def this(folderPath: Option[String] = None) {
    this(
      new DetectorViaFileDescription(folderPath).defineDetector()
    )
  }

  def detectOnImage(image: Mat): DetectionOutput = { detect(matToTensor(image)) }

  def getImageByPath(pathToImage: String): Mat = { imread(pathToImage) }

  def getLabel(index: Int): String = _detectorModel.getLabelByIndex(index)

  def sourceCamera(cameraDeviceIdx: Int)(implicit system: ActorSystem): Source[Frame, NotUsed] = {
    val sourceGraph: Graph[SourceShape[Frame], NotUsed] = new CameraSource(cameraDeviceIdx)
    Source.fromGraph(sourceGraph)
  }

  def sourceVideo(pathToVideo: String)(implicit system: ActorSystem): Source[Frame, NotUsed] = {
    val sourceGraph: Graph[SourceShape[Frame], NotUsed] = new FFmpegSource(pathToVideo)
    Source.fromGraph(sourceGraph)
  }

  // run the object detection model on an image
  private def detect(image: Tensor): DetectionOutput = {
    // retrieve the output placeholders
    val imagePlaceholder = _detectorModel.graph.getOutputByName("image_tensor:0")
    val detectionBoxes = _detectorModel.graph.getOutputByName("detection_boxes:0")
    val detectionScores = _detectorModel.graph.getOutputByName("detection_scores:0")
    val detectionClasses = _detectorModel.graph.getOutputByName("detection_classes:0")
    val numDetections = _detectorModel.graph.getOutputByName("num_detections:0")

    // set image as input parameter
    val feeds = Map(imagePlaceholder -> image)

    // Run the detection model
    val Seq(boxes, scores, classes, num) =
      _detectorModel.session.run(
        fetches = Seq(detectionBoxes, detectionScores, detectionClasses, numDetections),
        feeds = feeds)
    DetectionOutput(boxes, scores, classes, num)
  }

  // convert OpenCV tensor to TensorFlow tensor
  private def matToTensor(image: Mat): Tensor = {
    val imageRGB = new Mat
    cvtColor(image, imageRGB, COLOR_BGR2RGB) // convert channels from OpenCV GBR to RGB
    val imgBuffer = imageRGB.createBuffer[ByteBuffer]
    val shape = Shape(1, image.size.height, image.size.width, image.channels)
    Tensor.fromBuffer(UINT8, shape, imgBuffer.capacity, imgBuffer)
  }

  private class CameraSource(cameraDeviceIdx: Int) extends GraphStage[SourceShape[Frame]] {
    val output = Outlet[Frame]("CameraSource")
    override def shape: SourceShape[Frame] = SourceShape(output)

    private def buildGrabber(): OpenCVFrameGrabber = synchronized
    {
      val g = new OpenCVFrameGrabber(cameraDeviceIdx)
      g.start()
      g
    }

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with OutHandler {

        private lazy val grabber = buildGrabber()

        override def postStop(): Unit = {
          super.postStop()
          Option(grabber).foreach(_.close())
        }

        setHandler(output, this)

        override def onPull(): Unit =
          grabFrame().foreach(push(output, _))

        private def grabFrame(): Option[Frame] =
          Option(grabber.grab())
      }
  }

  private class FFmpegSource(pathToVideo: String) extends GraphStage[SourceShape[Frame]] {
    val output = Outlet[Frame]("FFmpegSpurce")
    override def shape: SourceShape[Frame] = SourceShape(output)

    private def buildGrabber(pixelFormat: Int = -1,
                             imageMode: ImageMode = ImageMode.COLOR): FFmpegFrameGrabber = synchronized
    {
      val g = new FFmpegFrameGrabber(pathToVideo)
      g.setPixelFormat(pixelFormat)
      g.setImageMode(imageMode)
      g.start()
      g
    }

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with OutHandler {

        private lazy val grabber = buildGrabber()

        override def postStop(): Unit = {
          super.postStop()
          Option(grabber).foreach(_.close())
        }

        setHandler(output, this)

        override def onPull(): Unit =
          grabFrame().foreach(push(output, _))

        private def grabFrame(): Option[Frame] =
          Option(grabber.grab())
      }
  }
}



