package ru.able

import com.typesafe.scalalogging.LazyLogging
import ru.able.controller.DetectorController
import ru.able.view.DetectorView

object AbleAIApp extends App with LazyLogging {

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

  val detectorController: DetectorController = new DetectorController(args.lift(2))
  val detector: DetectorView = new DetectorView(detectorController)

  val inputType: Option[String] = args.lift(0)
  inputType.getOrElse("") match {
    case "image" =>
      detector.detectOnImage(args(1))
    case "video" =>
      detector.detectOnVideo(args.lift(1))
    case "camera" =>
      detector.detectFromCamera(args.lift(1))
    case _ => printUsageAndExit()
  }
}
