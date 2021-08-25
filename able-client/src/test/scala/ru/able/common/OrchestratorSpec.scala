package ru.able.common

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import testutils.StopSystemAfterAll
import testutils.TestSystem.TestActorSystem

import scala.concurrent.duration._
import ru.able.camera.utils.settings.Settings
import ru.able.router.Orchestrator
import ru.able.router.PluginRegistryFSM.{Add, Remove}
import ru.able.router.model.{Plugin, Start, Stop}

class OrchestratorSpec extends TestKit(ActorSystem(TestActorSystem))
  with ImplicitSender
  with WordSpecLike
  with OneInstancePerTest
  with StopSystemAfterAll
  with Matchers
  with MockitoSugar
{
  private implicit val ec              = system.dispatcher
  private val switch                   = TestProbe()
  private val pluginRegistry           = TestProbe()
  private val settings                 = mock[Settings]
  private val duration: FiniteDuration = FiniteDuration(50, TimeUnit.MILLISECONDS)

  when(settings.getDuration(any[String], any[TimeUnit]))
    .thenReturn(duration)
  private val underTest = new Orchestrator(switch.ref, pluginRegistry.ref, settings)

  "Buncher" when {
    "addPlugin" should {
      "call plugin registry" in {
        val plugin = mock[Plugin]
        underTest.addPlugin(plugin)
        pluginRegistry.expectMsgAnyClassOf(duration, classOf[Add])
      }

      "remove plugin registry" in {
        val plugin = mock[Plugin]
        underTest.removePlugin(plugin)
        pluginRegistry.expectMsgAnyClassOf(duration, classOf[Remove])
      }
    }

    "start" should {
      "send start to Switch" in {
        underTest.start()
        switch.expectMsgAnyClassOf(duration, classOf[Start])
      }
    }

    "stop" should {
      "send stop to Switch" in {
        underTest.stop()
        switch.expectMsg(duration, Stop)
      }
    }
  }
}
