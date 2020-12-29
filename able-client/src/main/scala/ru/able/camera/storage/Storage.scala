package ru.able.camera.storage

import ru.able.camera.camera.CameraFrame

object Storage{
  case class Save(frame: CameraFrame)
}

trait Storage {
  def save(frame: CameraFrame)
}
