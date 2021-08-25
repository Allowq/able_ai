package ru.able.camera.framereader.model

import java.time.LocalDateTime

import org.bytedeco.javacpp.opencv_core.{IplImage, Mat}

case class CameraFrame(imgMat: Mat, date: LocalDateTime = LocalDateTime.now()) {
  def formattedDate(format: String): String = date.formatted(format)
}

case class MotionDetectFrame(maskedImg: IplImage, originalFrame: CameraFrame)
