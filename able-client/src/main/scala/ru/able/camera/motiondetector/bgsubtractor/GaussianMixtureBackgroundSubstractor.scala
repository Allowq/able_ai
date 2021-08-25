package ru.able.camera.motiondetector.bgsubtractor

import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_video.BackgroundSubtractorMOG2

import ru.able.camera.framereader.model.{CameraFrame, MotionDetectFrame}
import ru.able.camera.utils.MediaConversion

object GaussianMixtureBackgroundSubstractor {
  def apply(mog: BackgroundSubtractorMOG2, learningRate: Double): GaussianMixtureBackgroundSubstractor =
    new GaussianMixtureBackgroundSubstractor(mog, learningRate)
}

/**
  * Substracts foreground from background
  *
  * @param mog2bgSubstractor
  * @param learningRate
  */
class GaussianMixtureBackgroundSubstractor(mog2bgSubstractor: BackgroundSubtractorMOG2,
                                           learningRate: Double) extends BackgroundSubstractor with LazyLogging
{
  private val mask: Mat = new Mat()

  private def applyMask(source: Mat): IplImage = {
    val currentFrame = new Mat(source)
    mog2bgSubstractor.apply(currentFrame, mask, learningRate)

    val maskedImage = MediaConversion.toIplImage(mask)
    currentFrame.release()
    maskedImage
  }

  override def substractBackground(cf: CameraFrame): MotionDetectFrame =
    MotionDetectFrame(maskedImg = applyMask(cf.imgMat), originalFrame = cf)

  override def close(): Unit = {
    mog2bgSubstractor.close()
    mask.release()
  }
}
