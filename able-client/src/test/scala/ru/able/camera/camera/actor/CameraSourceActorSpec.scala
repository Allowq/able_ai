package ru.able.camera.camera.actor

import akka.actor.{ActorSystem, Props}
import akka.stream.{KillSwitch, Materializer}
import akka.stream.scaladsl.RunnableGraph
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{OneInstancePerTest, WordSpecLike}
import ru.able.camera.camera.graph.CameraReaderGraph.CameraSource
import ru.able.camera.camera.reader.{BroadCastRunnableGraph, BroadcastMaterializer}
import testutils.StopSystemAfterAll
import testutils.TestSystem.TestActorSystem

import scala.concurrent.Promise

class CameraSourceActorSpec
    extends TestKit(ActorSystem(TestActorSystem))
    with ImplicitSender
    with WordSpecLike
    with OneInstancePerTest
    with StopSystemAfterAll
    with MockitoSugar {

  implicit val materializer                      = Materializer.createMaterializer(system)
  private val killSwitch                         = mock[KillSwitch]
  private val graph: RunnableGraph[CameraSource] = null
  private val broadcastDummy                     = BroadCastRunnableGraph(graph)
  private val broadCastMateralizer               = mock[BroadcastMaterializer]
//  private val underTest =
//    TestActorRef(Props(new CameraSourceActor(broadCastMateralizer)(materializer)))

  private val promise = Promise[BroadCastRunnableGraph]()

  "CameraActorSpec" when {

    /*"happy path" should {
      "start" in {
        when(broadCastMateralizer.create(killSwitch)).thenReturn(promise)

        underTest ! Start(killSwitch)
        promise success broadcastDummy

        expectMsg(SourceInit(broadcastDummy))
        verify(broadCastMateralizer).create(killSwitch)
      }
    }

    "error handling" should {
      "sourceFactory throws exception when start message received" in {
        val cause = "cause"
        when(broadCastMateralizer.create(killSwitch)).thenReturn(promise)

        underTest ! Start(killSwitch)
        promise failure new Exception(cause)

        expectMsg(Error(cause))
        verify(broadCastMateralizer).create(killSwitch)
        verifyZeroInteractions(killSwitch)
      }

      "broadcastFactory throws exception when start message received" in {
        val cause = "cause"
        when(broadCastMateralizer.create(killSwitch))
          .thenThrow(new RuntimeException(cause))

        underTest ! Start(killSwitch)

        expectMsg(Error(cause))
        verify(broadCastMateralizer).create(killSwitch)
        verifyZeroInteractions(killSwitch)
      }
    }*/
  }
}
