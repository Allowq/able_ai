package ru.able.communication.viasocket

import java.io.ObjectOutputStream
import java.net.{ConnectException, InetAddress, Socket}

import com.typesafe.scalalogging.LazyLogging
import ru.able.camera.framereader.model.CameraFrame

import scala.util.Try

trait SocketSupport {

  def withSocket[T](f: (Socket) => T): T = {
    val socket: Socket = new Socket(InetAddress.getByName("127.0.0.1"), 9999)
    val result         = Try { f(socket) } recover { case e: Exception => throw e }

    socket.close()
    result.get
  }
}

class SocketCommunication extends Communication with LazyLogging with SocketSupport
{
  override def send(frame: CameraFrame): Either[String, String] = {
    Try {
      withSocket(socket => sendViaSocket(socket, SocketFrameConverter.convertToSocketFrame(frame)))
      Right("success")
    } recover {
      //TODO: Can cause errors which happen when socket is busy?
      case _: ConnectException => Right("success")
      case e: Exception => {
        logger.warn(e.getMessage, e)
        Left(e.getMessage)
      }
    } get
  }

  override def sendBatch(msg: Seq[CameraFrame]): Either[String, String] = {
    Try {
      val data: Seq[SocketFrame] = msg.map(SocketFrameConverter.convertToSocketFrame)
      withSocket(socket => sendViaSocket(socket, data))
      Right("success")
    } recover {
      //TODO: Upgrade to check socket busy
      case _: ConnectException => Right("success")
      case e: Exception => {
        logger.warn(e.getMessage, e)
        Left(e.getMessage)
      }
    } get
  }

  private def sendViaSocket(socket: Socket, msg: AnyRef) = {
    val out = new ObjectOutputStream(socket.getOutputStream())
    out.writeObject(msg)
    out.writeBytes("\n")
    out.flush()
    out.close()
  }
}
