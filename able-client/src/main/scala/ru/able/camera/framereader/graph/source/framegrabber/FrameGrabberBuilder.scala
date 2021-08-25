package ru.able.camera.framereader.graph.source.framegrabber

import com.typesafe.scalalogging.LazyLogging
import javax.inject.Inject
import org.bytedeco.javacv.{FFmpegFrameGrabber, FrameGrabber}
import ru.able.camera.utils.settings.Settings

sealed trait FrameGrabberBuilder {
  def create(): FrameGrabber
}

class FFmpegFrameGrabberBuilder @Inject()(settings: Settings) extends FrameGrabberBuilder with LazyLogging
{
  override def create(): FrameGrabber = synchronized {
    val grabber = new FFmpegFrameGrabber(settings.cameraPath())
//    grabber.setPixelFormat(org.bytedeco.javacpp.avutil.AV_PIX_FMT_RGB0)
    grabber.setFormat(settings.cameraFormat)
    grabber.setImageWidth(settings.getInt("camera.width"))
    grabber.setImageHeight(settings.getInt("camera.height"))
    grabber.setFrameRate(settings.getInt("camera.frameRate"))
    grabber.setOption("-r", settings.getInt("camera.fps").toString)

    logger.info(s"FPS: ${settings.getInt("camera.fps")}")
    logger.info(s"FPS framerate ${grabber.getFrameRate}")

    grabber
  }
}
