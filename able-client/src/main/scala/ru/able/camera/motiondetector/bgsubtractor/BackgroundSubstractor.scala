package ru.able.camera.motiondetector.bgsubtractor

import ru.able.camera.framereader.model.{CameraFrame, MotionDetectFrame}

trait BackgroundSubstractor extends AutoCloseable {
  def substractBackground(frame: CameraFrame): MotionDetectFrame
}
