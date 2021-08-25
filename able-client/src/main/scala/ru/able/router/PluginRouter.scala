package ru.able.router

import ru.able.camera.framereader.graph.broadcast.BroadcastRunnableGraph
import ru.able.router.model.Orchestrator.GlobalKillSwitch
import ru.able.router.model.{AdvancedPluginStart, Plugin}

object PluginRouter {
  def empty(): PluginRouter = PluginRouter(Seq.empty, None, None)
}

/**
  * Routing start/stop messages to Plugins
  *
  * @param plugins
  * @param ks
  * @param bs
  */
case class PluginRouter(plugins: Seq[Plugin], ks: Option[GlobalKillSwitch], bs: Option[BroadcastRunnableGraph]) {

  def addPlugin(plugin: Plugin): PluginRouter = {
    if (plugins.contains(plugin)) this
    else PluginRouter(plugins :+ plugin, ks, bs)
  }

  def removePlugin(plugin: Plugin): PluginRouter = PluginRouter(plugins diff Seq(plugin), ks, bs)

  def start(ps: AdvancedPluginStart): PluginRouter = {
    plugins.foreach(_.start(ps))
    PluginRouter(plugins, Some(ps.ks), Some(ps.broadcast))
  }

  def stop(): PluginRouter = {
    plugins.foreach(_.stop())
    PluginRouter(plugins, None, None)
  }
}
