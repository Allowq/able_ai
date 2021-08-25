package ru.able.router.model

import akka.stream.SharedKillSwitch

object Orchestrator {
  case class GlobalKillSwitch(sharedKillSwitch: SharedKillSwitch) {
    def shutdown(): Unit = sharedKillSwitch.shutdown()
  }
}
