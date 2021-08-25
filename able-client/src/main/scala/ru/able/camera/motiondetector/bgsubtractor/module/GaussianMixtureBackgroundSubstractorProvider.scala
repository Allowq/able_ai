package ru.able.camera.motiondetector.bgsubtractor.module

import com.google.inject.{Inject, Provider}
import org.bytedeco.javacpp.opencv_video.BackgroundSubtractorMOG2
import ru.able.camera.motiondetector.bgsubtractor.GaussianMixtureBackgroundSubstractor
import ru.able.camera.utils.settings.Settings

class GaussianMixtureBackgroundSubstractorProvider @Inject()(mog2bgSubstractor: BackgroundSubtractorMOG2, settings: Settings)
  extends Provider[GaussianMixtureBackgroundSubstractor]
{
  override def get(): GaussianMixtureBackgroundSubstractor =
    GaussianMixtureBackgroundSubstractor(
      mog2bgSubstractor,
      settings.motionDetectOptions().getOrElse("learningRate", 0.1).asInstanceOf[Double]
    )
}
