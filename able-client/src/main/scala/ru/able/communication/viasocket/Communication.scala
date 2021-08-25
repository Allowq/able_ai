package ru.able.communication.viasocket

import ru.able.camera.framereader.model.CameraFrame

trait Communication {

  def send(msg: CameraFrame): Either[String, String]
  def sendBatch(msg: Seq[CameraFrame]): Either[String, String]
}
