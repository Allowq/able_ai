package ru.able.camera.motiondetector.bgsubtractor

import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_video.BackgroundSubtractorMOG2
import org.bytedeco.javacv.{Frame, OpenCVFrameConverter}
import ru.able.camera.camera.CameraFrame
import ru.able.camera.camera.MotionDetectFrame
import ru.able.camera.utils.MediaConversion

@deprecated
object GaussianMixtureBasedBackgroundSubstractor {
  @deprecated
  def apply(mog: BackgroundSubtractorMOG2, learningRate: Double): GaussianMixtureBasedBackgroundSubstractor =
    new GaussianMixtureBasedBackgroundSubstractor(mog, learningRate)
}

/**
  * Substracts foreground from background
  *
  * @param backgroundSubtractorMOG2
  * @param learningRate
  */
class GaussianMixtureBasedBackgroundSubstractor(backgroundSubtractorMOG2: BackgroundSubtractorMOG2,
                                                learningRate: Double) extends BackgroundSubstractor with LazyLogging
{
  private val mask: Mat = new Mat()

  private def applyMask(source: Mat): IplImage = {
    backgroundSubtractorMOG2.apply(source, mask, learningRate)
    val maskedImage = MediaConversion.toIplImage(mask)
    maskedImage
  }

  override def substractBackground(cf: CameraFrame): MotionDetectFrame =
    MotionDetectFrame(applyMask(cf.imgMat), cf)

  override def close(): Unit = {
    backgroundSubtractorMOG2.close()
    mask.release()
  }
}
