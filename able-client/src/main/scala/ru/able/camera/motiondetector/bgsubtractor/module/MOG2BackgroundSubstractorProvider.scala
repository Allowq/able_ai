package ru.able.camera.motiondetector.bgsubtractor.module

import com.google.inject.{Inject, Provider}
import org.bytedeco.javacpp.opencv_video.BackgroundSubtractorMOG2
import ru.able.camera.motiondetector.bgsubtractor.MOG2BackgroundSubtractorFactory
import ru.able.camera.utils.settings.Settings

class MOG2BackgroundSubstractorProvider @Inject()(settings: Settings) extends Provider[BackgroundSubtractorMOG2]
{
  override def get(): BackgroundSubtractorMOG2 =
    MOG2BackgroundSubtractorFactory(
      lengthOfHistory = settings.motionDetectOptions().getOrElse("history", 200).asInstanceOf[Int],
      threshold       = settings.motionDetectOptions().getOrElse("threshold", 20).asInstanceOf[Int],
      shadowDetect    = settings.motionDetectOptions().getOrElse("shadowDetect", false).asInstanceOf[Boolean]
    )
}