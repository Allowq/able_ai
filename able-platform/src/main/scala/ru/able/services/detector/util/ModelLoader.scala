package ru.able.services.detector.util

import java.io.File
import java.net.URL

import sys.process._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

object ModelLoader extends LazyLogging {

  private lazy val _config = ConfigFactory.defaultApplication().resolve().getConfig("modelDescription")

  sealed trait LoadStatus

  case object NeedDownloadModel extends LoadStatus
  case object NeedExtractModel extends LoadStatus
  case object ModelDownloaded extends LoadStatus
  case object NeedDownloadLabelMap extends LoadStatus

  def initializeModel(implicit ec: ExecutionContext): Future[Unit] = {
    val p = Promise[Unit]()
    Future.sequence(Seq(checkLocalModelFiles, checkLocalLabelMapFiles)).onComplete {
      case Success(_) => p.success()
      case Failure(ex) => p.failure(ex)
    }
    p.future
  }

  private def checkLocalModelFiles: Future[Unit] = {
    val status = (checkExistLocalModelFiles && checkExistInferenceGraph, checkExistArchivedModelFiles) match {
      case (false, false) =>
        logger.info(s"Couldn\'t find local object detection model. Please waiting for download model from $modelURL")
        NeedDownloadModel
      case (false, true) =>
        logger.info(s"Loading local object detection model.")
        NeedExtractModel
      case (true, _) =>
        ModelDownloaded
    }

    status match {
      case ModelDownloaded  => Future.successful()
      case _                => downloadAndExtractModel(status)
    }
  }

  private def downloadAndExtractModel(status: LoadStatus): Future[Unit] = {
    val p = Promise[Unit]()

    Try {
      status match {
        case NeedDownloadModel => {
          new URL(modelURL) #> new File(defaultModelPath + ".tar.gz") !!;
          logger.info("Downloading has finished.")
          val cmd = s"tar -xzf $defaultModelPath.tar.gz -C $baseDir"
          cmd.!!
          p.success()
        }
        case NeedExtractModel =>
          val cmd = s"tar -xzf $defaultModelPath.tar.gz -C $baseDir"
          cmd.!!
          p.success()
        case ModelDownloaded =>
          p.success()
      }
    } recover { case ex: Exception =>
      logger.warn("Cannot download and extract model for detection! App start unsuccessful.")
      p.failure(ex)
    }

    p.future
  }

  private def checkLocalLabelMapFiles: Future[Unit] = {
    val p = Promise[Unit]()
    Try {
      if (checkExistLabelMapFiles) {
        p.success()
      } else {
        logger.info(s"Couldn\'t find local labels for detection model. Please waiting for download model from $labelMapURL")
        new URL(labelMapURL) #> new File(labelMapPath) !!;
        p.success()
      }
    } recover { case ex: Exception =>
      logger.warn("Cannot download labels for detection! App start unsuccessful.")
      p.failure(ex)
    }
    p.future
  }

  private def checkExistLocalModelFiles: Boolean    = new File(defaultModelPath).exists()
  private def checkExistInferenceGraph: Boolean     = new File(inferenceGraphPath).exists()
  private def checkExistArchivedModelFiles: Boolean = new File(defaultModelPath + ".tar.gz").exists()
  private def checkExistLabelMapFiles: Boolean      = new File(labelMapPath).exists()

  private def baseDir: String             = _config.getString("baseDir")
  private def modelURL: String            = _config.getString("modelUrl")
  private def labelMapURL: String         = _config.getString("labelMapUrl")
  private def defaultModelPath: String    = _config.getString("defaultModelPath")
  private def labelMapPath: String        = _config.getString("labelMapPath")
  private def inferenceGraphPath: String  = _config.getString("inferenceGraphPath")
}