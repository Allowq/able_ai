package ru.able.camera.framegrabber

import javax.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacv.{FFmpegFrameGrabber, FrameGrabber}
import ru.able.camera.utils.settings.Settings

sealed trait FrameGrabberBuilder {
  def create(): FrameGrabber
}

class FFmpegFrameGrabberBuilder @Inject()(settings: Settings) extends FrameGrabberBuilder with LazyLogging{

  override def create(): FrameGrabber = synchronized {
//    val grabber = new FFmpegFrameGrabber("settings.cameraPath")
    val grabber = new FFmpegFrameGrabber("0:none")
//    grabber.setFormat(settings.cameraFormat)
    grabber.setFormat("avfoundation")
    grabber.setImageWidth(settings.getInt("camera.width"))
    grabber.setImageHeight(settings.getInt("camera.height"))
    grabber.setFrameRate(settings.getInt("camera.frameRate"))
    grabber.setOption("-r", settings.getInt("camera.fps").toString)
    logger.info(s"FPS set to ${settings.getInt("camera.fps")}")
    logger.info(s"FPS framerate ${grabber.getFrameRate}")
    settings.cameraOptions.foreach { case (k, v) => grabber.setOption(k, v.toString) }
    grabber
  }
}
