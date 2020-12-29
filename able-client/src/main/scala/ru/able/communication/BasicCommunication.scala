package ru.able.communication

import java.io.{ObjectOutputStream}
import java.net.InetAddress
import java.net.Socket
import java.net.ConnectException
import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.opencv_core.IplImage
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_core.cvReleaseData
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.opencv_imgcodecs
import org.bytedeco.javacpp.opencv_imgcodecs.CV_IMWRITE_JPEG_QUALITY
import org.bytedeco.javacpp.opencv_imgcodecs.cvEncodeImage
import ru.able.camera.camera.CameraFrame

import scala.util.Try

class SocketFrameConverter() {

  def convert(frame: CameraFrame): SocketFrame =
    SocketFrame(toBytes(frame.image, ".jpg"), frame.date)

  private def asJpeg(image: IplImage, quality: Int = 80): Array[Byte] = {
    val matrix =
      cvEncodeImage(".jpg", image.asCvMat(), Array(CV_IMWRITE_JPEG_QUALITY, quality, 0))
    val ptr  = matrix.data_ptr()
    val data = new Array[Byte](matrix.size())
    ptr.get(data, 0, matrix.size())
    matrix.release()
    data
  }

  //  @throws[Exception]
  //  def getnextframe(frame: Array[Byte]): Int = {
  //    val image = converterToIplImage.convert(grabber.grab)
  //    val frame_length = toBytes(image, ".jpg")
  //    System.arraycopy(frame_length, 0, frame, 0, frame_length.length)
  //    frame_length.length
  //  }

  def toBytes(image: IplImage, format: String): Array[Byte] = {
    val m           = cvEncodeImage(format, image.asCvMat)
    val bytePointer = m.data_ptr
    val imageData   = new Array[Byte](m.size)
    bytePointer.get(imageData, 0, m.size)
    cvReleaseData(m)
    imageData
  }

  def convert(socketFrame: SocketFrame): Mat = {
    val bytePointer = new BytePointer(socketFrame.data: _*)

//    val file = new File(s"g:\\cucc\\${socketFrame.date.toString.replace(":", "_")}.jpg")
//    import java.io.FileOutputStream
//    try {
//      val fos = new FileOutputStream(file)
//      try {
//        fos.write(socketFrame.data)
//        fos.close()
//        println(s"file saved to ${file.getAbsolutePath}")
//      } finally if (fos != null) fos.close()
//    }

    opencv_imgcodecs.imdecode(new Mat(bytePointer, false), opencv_imgcodecs.IMREAD_UNCHANGED)
  }
}

@SerialVersionUID(1664L)
case class SocketFrame(data: Array[Byte], date: LocalDateTime) extends java.io.Serializable {}

trait SocketSupport {

  def withSocket[T](f: (Socket) => T): T = {
    val socket: Socket = new Socket(InetAddress.getByName("127.0.0.1"), 9999)
    val result         = Try { f(socket) } recover { case e: Exception => throw e }
    socket.close()
    result.get
  }
}

class BasicCommunication extends Communication with LazyLogging with SocketSupport {

  val converter = new SocketFrameConverter()

  override def send(frame: CameraFrame): Either[String, String] =
    Try {
      withSocket(socket => sendViaSocket(socket, converter.convert(frame)))
      Right("success")
    } recover {
      // TODO: Can cause errors which happen when socket is busy?
      case _: ConnectException => Right("success")
      case e: Exception => {
        logger.warn(e.getMessage, e)
        Left(e.getMessage)
      }
    } get

  private def sendViaSocket(socket: Socket, msg: AnyRef) = {
    val out = new ObjectOutputStream(socket.getOutputStream())
    out.writeObject(msg)
    out.writeBytes("\n")
    out.flush()
    out.close()
  }

  override def sendBatch(msg: Seq[CameraFrame]): Either[String, String] = {
    Try {
      val data = msg.map(converter.convert).toList
      withSocket(socket => sendViaSocket(socket, data))
      Right("success")
    } recover {
      case e: Exception => {
        logger.warn(e.getMessage, e)
        Left(e.getMessage)
      }
    } get
  }
}
