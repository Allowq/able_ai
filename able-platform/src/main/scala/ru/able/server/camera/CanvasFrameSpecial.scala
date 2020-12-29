package ru.able.server.camera

import java.time.LocalDateTime

import org.bytedeco.javacpp.{BytePointer, opencv_imgcodecs}
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacv.Frame
import ru.able.util.MediaConversion

@SerialVersionUID(1664L)
case class SocketFrame(data: Array[Byte], date: LocalDateTime) extends java.io.Serializable {}

object CanvasFrameSpecial {
  private def convert(socketFrame: SocketFrame): Mat = {
    val bytePointer = new BytePointer(socketFrame.data: _*)
    opencv_imgcodecs.imdecode(new Mat(bytePointer, false), opencv_imgcodecs.IMREAD_UNCHANGED)
  }
}

class CanvasFrameSpecial(val frame: Frame, val date: LocalDateTime) {
  def this(cf: SocketFrame) {
    this(MediaConversion.toFrame(CanvasFrameSpecial.convert(cf)), cf.date)
  }
}