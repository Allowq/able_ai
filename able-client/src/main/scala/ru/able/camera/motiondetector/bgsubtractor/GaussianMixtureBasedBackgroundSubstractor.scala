package ru.able.camera.motiondetector.bgsubtractor

import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_video.BackgroundSubtractorMOG2
import org.bytedeco.javacv.OpenCVFrameConverter

import ru.able.camera.camera.CameraFrame
import ru.able.camera.camera.MotionDetectFrame

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

  private def applyMask(source: IplImage): IplImage = {
//    val nnn          = org.bytedeco.javacpp.opencv_core.cvCloneImage(source)
//    val currentFrame = new Mat(nnn)
    val currentFrame = new Mat(source)
    backgroundSubtractorMOG2.apply(currentFrame, mask, learningRate)
    val converter   = new OpenCVFrameConverter.ToIplImage()
    val maskedImage = converter.convert(converter.convert(mask))
//    val maskedImage = new IplImage(mask)
    currentFrame.release()
    maskedImage
  }

  override def substractBackground(frame: CameraFrame): MotionDetectFrame =
    MotionDetectFrame(masked = applyMask(frame.image), originalFrame = frame)

  override def close(): Unit = {
    backgroundSubtractorMOG2.close()
    mask.release()
  }
}
