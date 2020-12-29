package ru.able.camera.motiondetector.bgsubtractor.module

import com.google.inject.AbstractModule
import org.bytedeco.javacpp.opencv_video.BackgroundSubtractorMOG2

import ru.able.camera.motiondetector.bgsubtractor.GaussianMixtureBasedBackgroundSubstractor

class BackgroundSubstractorInjector extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[BackgroundSubtractorMOG2])
      .toProvider(classOf[BackgroundSubstractorMOG2Provider])
    bind(classOf[GaussianMixtureBasedBackgroundSubstractor])
      .toProvider(classOf[GaussianMixtureBasedBackgroundSubstractorProvider])
  }
}
