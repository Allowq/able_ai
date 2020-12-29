package ru.able.camera.utils

import org.bytedeco.javacpp.opencv_imgcodecs.cvSaveImage
import org.bytedeco.javacv.Frame

object CVUtils {
  def apply: CVUtils = new CVUtils()
}

/**
  * Wrapper class for CV static methods
  */
class CVUtils{
  def saveImage(path: String, frame: Frame): Unit = cvSaveImage(path, MediaConversion.toIplImage(frame))
}