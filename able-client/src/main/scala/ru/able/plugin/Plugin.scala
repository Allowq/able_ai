package ru.able.plugin

import ru.able.router.messages.AdvancedPluginStart

trait Plugin {
  def start(p: AdvancedPluginStart)
  def stop()
}
