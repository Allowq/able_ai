package ru.able.router.model

trait Plugin {
  def start(p: AdvancedPluginStart)
  def stop()
}
