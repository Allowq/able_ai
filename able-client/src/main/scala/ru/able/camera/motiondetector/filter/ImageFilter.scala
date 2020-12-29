package ru.able.camera.motiondetector.filter

import org.bytedeco.javacpp.opencv_core.Mat

trait ImageFilter {
  def filter(frame: Mat): Mat
}
