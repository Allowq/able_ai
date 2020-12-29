package ru.able.camera.motiondetector.filter

import org.bytedeco.javacpp.opencv_core.{Mat, extractChannel}

class GrayFilter extends ImageFilter {
  override def filter(frame: Mat): Mat = {
    val grayFrame = new Mat()
    extractChannel(frame, grayFrame, 0)
    grayFrame
  }
}
