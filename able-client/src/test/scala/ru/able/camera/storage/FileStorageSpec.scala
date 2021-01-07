package ru.able.camera.storage

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import org.bytedeco.javacpp.opencv_core.{IplImage, Mat}
import org.bytedeco.javacv.Frame
import org.scalatest.{OneInstancePerTest, WordSpecLike}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.{verify, verifyNoMoreInteractions, when}
import ru.able.camera.camera.CameraFrame
import ru.able.camera.storage.Storage.Save
import ru.able.camera.utils.{CVUtils, MediaConversion}
import testutils.StopSystemAfterAll
import testutils.TestSystem.TestActorSystem

class FileStorageSpec extends TestKit(ActorSystem(TestActorSystem))
  with WordSpecLike
  with OneInstancePerTest
  with StopSystemAfterAll
  with MockitoSugar {

  private val filePath = ""
  private val timestamp = "yyyyMMdd"
  private val cvUtils = mock[CVUtils]
  private val underTest =
    TestActorRef(new FileStorage(cvUtils, filePath, timestamp))

  "A FileStorage" can {

    "save image" should {

      "call frame with correct parameters" in {
        // Given
        val frame = mock[CameraFrame]
        // When
        underTest ! Save(frame)
        // Then
        verify(frame).formattedDate(timestamp)
        verify(frame).imgMat
        verifyNoMoreInteractions(frame)
      }

//      "call CVUtils with correct parameters" in {
//        // Given
//        val cf = mock[CameraFrame]
//        val imgMat = mock[Mat]
//        val expectedPath = s"$filePath/$timestamp"
//        when(cf.formattedDate(timestamp)).thenReturn(timestamp)
//        when(cf.imgMat).thenReturn(imgMat)
//        // When
//        underTest ! Save(cf)
//        // Then
//        verify(cvUtils).saveImage(expectedPath, imgMat)
//        verifyNoMoreInteractions(cvUtils)
//      }
    }
  }
}
