package ru.able.camera.motiondetector.bgsubtractor.module

import com.google.inject.{Inject, Provider}
import org.bytedeco.javacpp.opencv_video.BackgroundSubtractorMOG2
import ru.able.camera.motiondetector.bgsubtractor.GaussianMixtureBasedBackgroundSubstractor
import ru.able.camera.utils.settings.Settings

class GaussianMixtureBasedBackgroundSubstractorProvider @Inject()(backgroundSubtractorMOG2: BackgroundSubtractorMOG2,
                                                                  settings: Settings)
  extends Provider[GaussianMixtureBasedBackgroundSubstractor] {

  private val learningRate = settings.motionDetectOptions().getOrElse("learningRate", 0.1).asInstanceOf[Double]

  override def get(): GaussianMixtureBasedBackgroundSubstractor =
    GaussianMixtureBasedBackgroundSubstractor(backgroundSubtractorMOG2, learningRate)
}
