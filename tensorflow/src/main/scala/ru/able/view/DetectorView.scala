package ru.able.view

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import javax.swing.WindowConstants
import org.bytedeco.javacpp.opencv_core.{FONT_HERSHEY_PLAIN, LINE_AA, Mat, Point, Scalar}
import org.bytedeco.javacpp.opencv_imgproc.{putText, rectangle}
import org.bytedeco.javacv.{CanvasFrame, OpenCVFrameConverter}
import ru.able.controller.{DetectorController, MediaConversion}
import ru.able.model.DetectionOutput

import scala.concurrent.Await
import scala.concurrent.duration._

final class DetectorView (val controller: DetectorController) {

  implicit lazy val actorSystem: ActorSystem = {
    val as = ActorSystem("detector-actorSystem")
    scala.sys.addShutdownHook(Await.result(as.terminate(), 5.seconds))
    as
  }

  implicit lazy val materializer: Materializer = {
    Materializer.createMaterializer(actorSystem)
  }

  // run detector on a single image
  def detectOnImage(pathToImage: String): Unit = {
    val canvasFrame = new CanvasFrame("Able AICatcher")
    canvasFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE) // exit when the canvas frame is closed

    val image = controller.getImageByPath(pathToImage)
    canvasFrame.setCanvasSize(1280, 720)

    val detectionOutput = controller.detectOnImage(image)
    drawBoundingBoxes(image, detectionOutput)
    canvasFrame.showImage(new OpenCVFrameConverter.ToMat().convert(image))
    canvasFrame.waitKey(0)
    canvasFrame.dispose()
  }

  def detectOnVideo(pathToVideo: String): Unit = {
    val canvasFrame = new CanvasFrame("Able AI Catcher")
    canvasFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE) // exit when the canvas frame is closed
    val videoSource = controller.sourceVideo(pathToVideo)(materializer)
        .mapAsync(1)(MediaConversion.toAsyncMat)
        .mapAsync(1)(MediaConversion.asyncHorizontal)
        .map(img => {
          drawBoundingBoxes(img, controller.detectOnImage(img))
        })
        .mapAsync(1)(MediaConversion.toAsyncFrame)
        .map(canvasFrame.showImage)
        .to(Sink.ignore)
    videoSource.run()
  }

  def detectFromCamera(cameraDeviceIdx: Int): Unit = {
    val canvasFrame = new CanvasFrame("Able AI Catcher")
    canvasFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE) // exit when the canvas frame is closed

    val cameraSource = controller.sourceCamera(cameraDeviceIdx)
      .mapAsync(1)(MediaConversion.toAsyncMat)
      .mapAsync(1)(MediaConversion.asyncHorizontal)
      .map(img => {
        drawBoundingBoxes(img, controller.detectOnImage(img))
      })
      .mapAsync(1)(MediaConversion.toAsyncFrame)
      .map(canvasFrame.showImage)
      .to(Sink.ignore)
    cameraSource.run()
  }

  // draw boxes with class and score around detected objects
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
        val label = controller.getLabel(detectionOutput.classes(0, i).scalar.asInstanceOf[Float].toInt)

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
