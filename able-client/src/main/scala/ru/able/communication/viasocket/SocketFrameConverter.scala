package ru.able.communication.viasocket

import java.time.LocalDateTime

import org.bytedeco.javacpp.opencv_core.{IplImage, Mat, cvReleaseData}
import org.bytedeco.javacpp.opencv_imgcodecs.{CV_IMWRITE_JPEG_QUALITY, cvEncodeImage}
import org.bytedeco.javacpp.{BytePointer, opencv_imgcodecs}
import ru.able.camera.framereader.model.CameraFrame
import ru.able.camera.utils.MediaConversion

@SerialVersionUID(1664L)
case class SocketFrame(data: Array[Byte], date: LocalDateTime) extends java.io.Serializable {}

object SocketFrameConverter {

  def convertToSocketFrame(cf: CameraFrame): SocketFrame =
    SocketFrame(toBytes(MediaConversion.toIplImage(cf.imgMat), ".jpg"), cf.date)

  def convertToMat(socketFrame: SocketFrame): Mat = {
    val bytePointer = new BytePointer(socketFrame.data: _*)
    opencv_imgcodecs.imdecode(new Mat(bytePointer, false), opencv_imgcodecs.IMREAD_UNCHANGED)
  }

  private def asJpeg(image: IplImage, quality: Int = 80): Array[Byte] = {
    val matrix       = cvEncodeImage(".jpg", image.asCvMat(), Array(CV_IMWRITE_JPEG_QUALITY, quality, 0))
    val bytePointer  = matrix.data_ptr()
    val imageData    = new Array[Byte](matrix.size())

    bytePointer.get(imageData, 0, matrix.size())
    matrix.release()
    imageData
  }

  private def toBytes(image: IplImage, format: String): Array[Byte] = {
    val m           = cvEncodeImage(format, image.asCvMat)
    val bytePointer = m.data_ptr
    val imageData   = new Array[Byte](m.size)

    bytePointer.get(imageData, 0, m.size)
    cvReleaseData(m)
    imageData
  }
}