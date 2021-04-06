package ru.able.common

import akka.stream.SharedKillSwitch

object Switches {
  case class GlobalKillSwitch(sharedKillSwitch: SharedKillSwitch) {
    def shutdown(): Unit = sharedKillSwitch.shutdown()
  }
}
