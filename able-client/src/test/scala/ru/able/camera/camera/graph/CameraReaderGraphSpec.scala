package ru.able.camera.camera.graph

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfter, OneInstancePerTest, WordSpecLike}
import org.scalatest.mockito.MockitoSugar

import testutils.StopSystemAfterAll
import testutils.TestSystem.TestActorSystem

class CameraReaderGraphSpec extends TestKit(ActorSystem(TestActorSystem))
  with WordSpecLike
  with OneInstancePerTest
  with StopSystemAfterAll
  with BeforeAndAfter
  with MockitoSugar
{
  implicit val materializer = Materializer.createMaterializer(system)
//
//  private val webcamSource = mock[Source[Frame, NotUsed]]
//  private val tickingSource = mock[Source[Int, Cancellable]]
//  private val killSwitch = mock[SharedKillSwitch]
//  private val underTest = new CameraReaderGraph(webcamSource, tickingSource, killSwitch)
//
//  "xxxxxxxxxxx" should {
//
//    "xxxxxxxxxxxxxxxxxxxxx" in {
//
//      underTest.createGraph()
//
//
//    }
//
//  }
}
