package ru.able.camera.utils

import org.bytedeco.javacpp.opencv_core.IplImage
import org.bytedeco.javacpp.opencv_imgcodecs.cvSaveImage

object CVUtils {
  def apply: CVUtils = new CVUtils()
}

/**
  * Wrapper class for CV static methods
  */
class CVUtils{

  def saveImage(path: String, image: IplImage): Unit = cvSaveImage(path, image)

}