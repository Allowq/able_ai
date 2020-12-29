package ru.able.camera.storage

import akka.actor.Actor
import ru.able.camera.storage.Storage._
import ru.able.camera.utils.{CVUtils, MediaConversion}
import ru.able.camera.camera.CameraFrame

class FileStorage(cvUtils: CVUtils,
                  filePath: String = ".",
                  timestamp: String = "yyyy_MM_dd__HH_mm_ss.SS")
  extends Actor with Storage {

  override def receive: Receive = {
    case Save(frame) => save(frame)
  }

  override def save(cf: CameraFrame): Unit =
    cvUtils.saveImage(s"$filePath/${cf.formattedDate(timestamp)}", MediaConversion.toFrame(cf.imgMat))
}
