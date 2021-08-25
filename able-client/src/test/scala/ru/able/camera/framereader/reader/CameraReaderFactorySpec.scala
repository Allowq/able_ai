package ru.able.camera.framereader.reader

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{AsyncWordSpecLike, Matchers, OneInstancePerTest}
import org.scalatest.mockito.MockitoSugar
import ru.able.camera.framereader.BroadcastMaterializer
import ru.able.camera.framereader.graph.broadcast.BroadcastRunnableGraph

import scala.concurrent.Promise
import testutils.StopSystemAfterAll
import testutils.TestSystem.TestActorSystem

class CameraReaderFactorySpec
    extends TestKit(ActorSystem(TestActorSystem))
    with ImplicitSender
    with AsyncWordSpecLike
    with OneInstancePerTest
    with StopSystemAfterAll
    with Matchers
    with MockitoSugar {

  implicit val materializer        = Materializer.createMaterializer(system)
  private val killSwitch           = None.orNull
  private val graph                = None.orNull
  private val broadcastDummy       = BroadcastRunnableGraph(graph)
  private val broadCastMateralizer = mock[BroadcastMaterializer]
  private val promise              = Promise[BroadcastRunnableGraph]()

//  private val underTest = new CameraReaderFactory(broadCastMateralizer)
//
//  "CameraReaderFactory" should {
//
//    "call broadCastMateralizer and return broadcast" in {
//      val gks = KillSwitches.GlobalKillSwitch(killSwitch)
//      when(broadCastMateralizer.create(gks)).thenReturn(Promise.successful(broadcastDummy))
//
//      val result = underTest.create(gks)
//
//      result.future.map(expectedBroadCast => {
//        expectedBroadCast shouldEqual broadcastDummy
//      })
//    }

//  }

}
