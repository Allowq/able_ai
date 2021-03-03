package ru.able.server.model

import java.time.LocalDateTime

import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.{BytePointer, opencv_imgcodecs}
import org.bytedeco.javacv.Frame
import ru.able.util.MediaConversion

@SerialVersionUID(1664L)
case class SocketFrame(data: Array[Byte], date: LocalDateTime) extends java.io.Serializable {}

object CanvasFrameSpecial {
  def apply(frame: Frame, date: LocalDateTime): CanvasFrameSpecial = new CanvasFrameSpecial(frame, date)
  
  def apply(sf: SocketFrame): CanvasFrameSpecial = new CanvasFrameSpecial(MediaConversion.toFrame(convert(sf)), sf.date)

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