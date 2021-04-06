package ru.able.camera.camera.reader

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{AsyncWordSpecLike, OneInstancePerTest}
import org.scalatest.mockito.MockitoSugar
import org.scalatest._
import testutils.StopSystemAfterAll
import testutils.TestSystem.TestActorSystem

import ru.able.camera.camera.graph.CameraReaderGraph.CameraSource
import ru.able.camera.camera.graph.factory.{CameraReaderGraphFactory, SourceBroadCastFactory}
import ru.able.common.Switches.GlobalKillSwitch
import ru.able.camera.utils.settings.Settings

object BroadcastMaterializerSpec{
  private val exception = new RuntimeException("message")
  private val nullPointerException = new NullPointerException
}

class BroadcastMaterializerSpec
    extends TestKit(ActorSystem(TestActorSystem))
    with ImplicitSender
    with AsyncWordSpecLike
    with OneInstancePerTest
    with StopSystemAfterAll
    with Matchers
    with MockitoSugar {

  private implicit val materializer     = Materializer.createMaterializer(system)
  private val killSwitch                = mock[GlobalKillSwitch]
  private val sourceDummy: CameraSource = null
  private val broadcastDummy: BroadCastRunnableGraph = mock[BroadCastRunnableGraph]
  private val broadcastFactory          = mock[SourceBroadCastFactory]
  private val cameraReaderFactory       = mock[CameraReaderGraphFactory]
  private val settings                  = mock[Settings]
  private val underTest = new BroadcastMaterializer(cameraReaderFactory, broadcastFactory, settings)(materializer)

  "BroadcastMateralizer" when {

    "create" should {
//      "return broadcast when Future timing out" in {
//        when(cameraReaderFactory.create(killSwitch)).thenReturn(sourceDummy)
//        when(broadcastFactory.create(sourceDummy)).thenReturn(broadcastDummy)
//        when(broadcastDummy.toFuture()).thenReturn(Future.failed(nullPointerException))
//
//        val result = underTest.create(killSwitch)
//
//        result.future map { event =>
//          verify(cameraReaderFactory).create(killSwitch)
//          verify(broadcastFactory).create(sourceDummy)
//          event shouldBe Future.failed(nullPointerException)
//        }
//      }

//      "return exception when exception occurs in Future" in {
//        when(cameraReaderFactory.create(killSwitch)).thenReturn(sourceDummy)
//        when(broadcastFactory.create(sourceDummy)).thenReturn(broadcastDummy)
//        when(broadcastDummy.toFuture()).thenReturn(Future.failed(nullPointerException))
//
//        val result = underTest.create(killSwitch)
//
//        result.future.failed map { event =>
//          verify(cameraReaderFactory).create(killSwitch)
//          verify(broadcastFactory).create(sourceDummy)
//          event shouldBe nullPointerException
//        }
//      }

//      "return exception when Future throws exception" in {
//        when(cameraReaderFactory.create(killSwitch)).thenReturn(sourceDummy)
//        when(broadcastFactory.create(sourceDummy)).thenReturn(broadcastDummy)
//        when(broadcastDummy.toFuture()).thenThrow(exception)
//
//        val result = underTest.create(killSwitch)
//
//        result.future.failed map { event =>
//          verify(cameraReaderFactory).create(killSwitch)
//          verify(broadcastFactory).create(sourceDummy)
//          event shouldBe exception
//        }
//      }

//      "return exception when stream stops before timeout" in {
//        when(cameraReaderFactory.create(killSwitch)).thenReturn(sourceDummy)
//        when(broadcastFactory.create(sourceDummy)).thenReturn(broadcastDummy)
//        when(broadcastDummy.toFuture()).thenReturn(Future.successful(Done))
//
//        val result = underTest.create(killSwitch)
//
//        result.future.failed map { event =>
//          verify(cameraReaderFactory).create(killSwitch)
//          verify(broadcastFactory).create(sourceDummy)
//          event.getMessage shouldBe StreamClosedError
//        }
//      }
    }
  }
}
