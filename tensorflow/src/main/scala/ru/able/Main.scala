package ru.able

import org.slf4j.LoggerFactory
import ru.able.controller.DetectorController
import ru.able.view.DetectorView

object Main {

  val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {

    def printUsageAndExit(): Unit = {
      Console.err.println(
        """
          |Usage: ObjectDetector image <file>|video <file>|camera <deviceno> [<modelpath>]
          |  <file>      path to an image/video file
          |  <deviceno>  camera device number (usually starts with 0)
          |  <modeldir>  optional path to the object detection model to be used. Default: ssd_inception_v2_coco_2018_01_28
          |""".stripMargin.trim)
      sys.exit(2)
    }

    if (args.length < 2) printUsageAndExit()

    val detectorController: DetectorController = new DetectorController(args.lift(2))
    val detector: DetectorView = new DetectorView(detectorController)

    val inputType = args(0)
    inputType match {
      case "image" =>
        detector.detectOnImage(args(1))
      case "video" =>
        detector.detectOnVideo(args(1))
      case "camera" =>
        detector.detectFromCamera(Integer.parseInt(args(1)))
      case _ => printUsageAndExit()
    }
  }
}
