package ru.able.camera.motiondetector.bgsubtractor.module

import com.google.inject.{Inject, Provider}
import org.bytedeco.javacpp.opencv_video.BackgroundSubtractorMOG2
import ru.able.camera.motiondetector.bgsubtractor.BackgroundSubtractorMOG2Factory
import ru.able.camera.utils.settings.Settings

class BackgroundSubstractorMOG2Provider @Inject()(settings: Settings) extends Provider[BackgroundSubtractorMOG2] {
  //TODO refactor this getOrElse mess
  // why fallbacks defined here?
  // asInstanceOf safety?
  private val lengthOfHistory = settings.motionDetectOptions().getOrElse("history", 200).asInstanceOf[Int]
  private val threshold = settings.motionDetectOptions().getOrElse("threshold", 20).asInstanceOf[Int]
  private val shadowDetect = settings.motionDetectOptions().getOrElse("shadowDetect", false).asInstanceOf[Boolean]

  override def get(): BackgroundSubtractorMOG2 =
    BackgroundSubtractorMOG2Factory(
      lengthOfHistory = lengthOfHistory,
      threshold = threshold,
      shadowDetect = shadowDetect
    )
}