package ru.able.camera.camera.reader

import akka.stream.SharedKillSwitch

object KillSwitches {
  case class GlobalKillSwitch(sharedKillSwitch: SharedKillSwitch) {
    def shutdown(): Unit = sharedKillSwitch.shutdown()
  }
}
