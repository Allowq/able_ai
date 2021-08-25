package ru.able.camera.framereader.plugin.stage

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.stream.stage.GraphStage
import akka.stream.testkit.TestPublisher
import akka.stream.testkit.scaladsl.TestSource
import akka.stream.{Materializer, SinkShape}
import akka.testkit.TestKit
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacv.{CanvasFrame, Frame}
import org.mockito.Mockito.{verify, verifyNoMoreInteractions, when}
import org.mockito.Mockito.{reset, times}
import ru.able.camera.framereader.model.CameraFrame
import ru.able.camera.utils.MediaConversion
import testutils.{ShapeSpec, StopSystemAfterAll}
import testutils.TestSystem.TestActorSystem

//class ShowImageStageSpec extends TestKit(ActorSystem(TestActorSystem))
//  with ShapeSpec
//  with StopSystemAfterAll {
//
//  implicit val materializer = Materializer.createMaterializer(system)
//
//  private val canvas = mock[CanvasFrame]
//  private val cameraFrame = mock[CameraFrame]
//  private val frame = mock[Frame]
//  private val imgMat = mock[Mat]
//
//  private val underTest: GraphStage[SinkShape[CameraFrame]] = new ShowImageStage(canvas)
//
//  before {
//    when(cameraFrame.imgMat).thenReturn(imgMat)
//    when(MediaConversion.toFrame(imgMat)).thenReturn(frame)
//  }
//
//  after {
//    verifyNoMoreInteractions(canvas, cameraFrame, frame, imgMat)
//    reset(canvas, cameraFrame, frame, imgMat)
//  }
//
//  "A ShowImageShape" should {
//    "call dependencies properly" in {
//      val upstream: TestPublisher.Probe[CameraFrame] = createGraphWithSink
//
//      upstream.sendNext(cameraFrame)
//
//      verify(MediaConversion).toFrame(imgMat)
//      verify(canvas).showImage(frame)
//    }
//
//    "exception in converter should not propagate" in {
//      when(MediaConversion.toFrame(imgMat))
//        .thenThrow(new RuntimeException("boom"))
//        .thenReturn(frame)
//
//      val upstream: TestPublisher.Probe[CameraFrame] = createGraphWithSink
//
//      upstream.sendNext(cameraFrame)
//      upstream.sendNext(cameraFrame)
//
//      verify(MediaConversion, times(2)).toFrame(imgMat)
//      verify(canvas).showImage(frame)
//    }
//  }
//
//  private def createGraphWithSink = {
//    val (upstream, _) =
//      TestSource.probe[CameraFrame]
//        .toMat(underTest)(Keep.both)
//        .run()
//    upstream
//  }
//}
