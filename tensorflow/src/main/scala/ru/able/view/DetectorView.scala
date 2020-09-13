package ru.able.view

import java.nio.ByteBuffer

import javax.swing.WindowConstants
import org.bytedeco.javacpp.opencv_core.{FONT_HERSHEY_PLAIN, LINE_AA, Mat, Point, Scalar}
import org.bytedeco.javacpp.opencv_imgcodecs.imread
import org.bytedeco.javacpp.opencv_imgproc.{COLOR_BGR2RGB, cvtColor, putText, rectangle}
import org.bytedeco.javacv.{CanvasFrame, FFmpegFrameGrabber, FrameGrabber, OpenCVFrameConverter, OpenCVFrameGrabber}
import org.platanios.tensorflow.api.{Shape, Tensor, UINT8}
import ru.able.controller.DetectorController
import ru.able.model.DetectionOutput

import scala.collection.Iterator.continually

final class DetectorView private (private val _controller: DetectorController) {

  def this(refController: DetectorController) {
    this(refController)
  }

  // run detector on a single image
  def detectOnImage(pathToImage: String): Unit = {
    val image = imread(pathToImage)
    val canvasFrame = new CanvasFrame("Object Detection")
    canvasFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE) // exit when the canvas frame is closed
    canvasFrame.setCanvasSize(image.size.width, image.size.height)
    val detectionOutput = _controller.detect(matToTensor(image))
    drawBoundingBoxes(image, detectionOutput)
    canvasFrame.showImage(new OpenCVFrameConverter.ToMat().convert(image))
    canvasFrame.waitKey(0)
    canvasFrame.dispose()
  }

  def detectOnVideo(pathToVideo: String): Unit = {
    val grabber = new FFmpegFrameGrabber(pathToVideo)
    detectSequence(grabber)
  }

  def detectFromCamera(cameraDeviceIdx: Int): Unit = {
    val grabber = new OpenCVFrameGrabber(cameraDeviceIdx)
    detectSequence(grabber)
  }

  // run detector on an image sequence
  private def detectSequence(grabber: FrameGrabber): Unit = {
    val canvasFrame = new CanvasFrame("Able AI Catcher", CanvasFrame.getDefaultGamma / grabber.getGamma)
    canvasFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE) // exit when the canvas frame is closed
    val converter = new OpenCVFrameConverter.ToMat()
    grabber.start()
    for (frame <- continually(grabber.grab())
      .takeWhile(_ != null && (grabber.getLengthInFrames == 0 || grabber.getFrameNumber < grabber.getLengthInFrames)))
    {
      val image = converter.convert(frame)
      if (image != null) { // sometimes the first few frames are empty so we ignore them
        val detectionOutput = _controller.detect(matToTensor(image)) // run our model
        drawBoundingBoxes(image, detectionOutput)
        if (canvasFrame.isVisible) { // show our frame in the preview
          canvasFrame.showImage(frame)
        }
      }
    }
    canvasFrame.dispose()
    grabber.stop()
  }

  // convert OpenCV tensor to TensorFlow tensor
  private def matToTensor(image: Mat): Tensor = {
    val imageRGB = new Mat
    cvtColor(image, imageRGB, COLOR_BGR2RGB) // convert channels from OpenCV GBR to RGB
    val imgBuffer = imageRGB.createBuffer[ByteBuffer]
    val shape = Shape(1, image.size.height, image.size.width, image.channels)
    Tensor.fromBuffer(UINT8, shape, imgBuffer.capacity, imgBuffer)
  }

  // draw boxes with class and score around detected objects
  private def drawBoundingBoxes(image: Mat, detectionOutput: DetectionOutput): Unit = {
    for (i <- 0 until detectionOutput.boxes.shape.size(1)) {
      val score = detectionOutput.scores(0, i).scalar.asInstanceOf[Float]

      if (score > 0.5) {
        val box = detectionOutput.boxes(0, i).entriesIterator.map(_.asInstanceOf[Float]).toSeq
        // we have to scale the box coordinates to the image size
        val ymin = (box(0) * image.size().height()).toInt
        val xmin = (box(1) * image.size().width()).toInt
        val ymax = (box(2) * image.size().height()).toInt
        val xmax = (box(3) * image.size().width()).toInt
        val label = _controller.getLabel(detectionOutput.classes(0, i).scalar.asInstanceOf[Float].toInt)

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
  }
}
