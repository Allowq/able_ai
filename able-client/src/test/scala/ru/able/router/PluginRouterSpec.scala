package ru.able.router

import akka.stream.KillSwitch
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.mockito.Mockito.verify

import ru.able.camera.camera.reader.BroadCastRunnableGraph
import ru.able.camera.camera.reader.KillSwitches.GlobalKillSwitch
import ru.able.plugin.Plugin
import ru.able.router.messages.{AdvancedPluginStart, PluginStart}

class PluginRouterSpec extends WordSpec with Matchers with MockitoSugar {

  "PluginRouter" when {

    "Add" should {

      "create a new Router with the plugin" in {
        val underTest = PluginRouter.empty
        val plugin    = mock[Plugin]

        val result = underTest.addPlugin(plugin)

        result shouldEqual PluginRouter(Seq(plugin), None, None)
      }

      "same object not added twice" in {
        val underTest = PluginRouter.empty
        val plugin    = mock[Plugin]

        val result = underTest.addPlugin(plugin).addPlugin(plugin)

        result shouldEqual PluginRouter(Seq(plugin), None, None)
      }

    }

    "Remove" should {

      "create a new Router without the plugin" in {
        val underTest = PluginRouter.empty
        val pluginA   = mock[Plugin]
        val pluginB   = mock[Plugin]

        val result = underTest.addPlugin(pluginA).addPlugin(pluginB).removePlugin(pluginA)

        result shouldEqual PluginRouter(Seq(pluginB), None, None)
      }

      "not throw exception if plugin not added" in {
        val underTest = PluginRouter.empty
        val pluginA   = mock[Plugin]
        val pluginB   = mock[Plugin]

        val result = underTest.addPlugin(pluginB).removePlugin(pluginA)

        result shouldEqual PluginRouter(Seq(pluginB), None, None)
      }

    }

    "Start" should {

      "create a new Router with the plugin and call start on plugins" in {
        val underTest = PluginRouter.empty
        val pluginA   = mock[Plugin]
        val pluginB   = mock[Plugin]
        val ks        = mock[GlobalKillSwitch]
        val bs        = mock[BroadCastRunnableGraph]
        val ps        = AdvancedPluginStart(ks, bs)

        val result = underTest.addPlugin(pluginA).addPlugin(pluginB).start(ps)

        result shouldEqual PluginRouter(Seq(pluginA, pluginB), Some(ks), Some(bs))

        verify(pluginA).start(ps)
        verify(pluginB).start(ps)
      }

      "throw exception if PluginStart is null" in {
        val underTest = PluginRouter.empty

        a[NullPointerException] should be thrownBy underTest.start(None.orNull)
      }

    }

    "Stop" should {

      "create a new Router with the plugin" in {
        val underTest = PluginRouter.empty
        val pluginA   = mock[Plugin]
        val pluginB   = mock[Plugin]
        val ks        = mock[KillSwitch]
        val bs        = mock[BroadCastRunnableGraph]
        val ps        = PluginStart(ks, bs)

        val result = underTest.addPlugin(pluginA).addPlugin(pluginB).stop()

        result shouldEqual PluginRouter(Seq(pluginA, pluginB), None, None)

        verify(pluginA).stop()
        verify(pluginB).stop()
      }

    }
  }

}
