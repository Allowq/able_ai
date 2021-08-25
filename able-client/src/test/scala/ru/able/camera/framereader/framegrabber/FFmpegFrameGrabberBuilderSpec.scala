package ru.able.camera.framereader.framegrabber

import org.scalatest.{BeforeAndAfter, WordSpec}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.{verify, verifyNoMoreInteractions, when}
import ru.able.camera.framereader.graph.source.framegrabber.FFmpegFrameGrabberBuilder
import ru.able.camera.utils.settings.Settings

class FFmpegFrameGrabberBuilderSpec extends WordSpec with MockitoSugar with BeforeAndAfter {

  private val settings = mock[Settings]
  private val underTest = new FFmpegFrameGrabberBuilder(settings)

  after {
    verifyNoMoreInteractions(settings)
  }

  "A FFmpegFrameGrabberBuilder" should {

//    "call correct settings" in {
//      when(settings.cameraOptions()).thenReturn(Map[String, String]())
//
//      underTest.create()
//
//      verify(settings).cameraPath()
//      verify(settings).cameraFormat()
//      verify(settings).cameraOptions()
//    }
  }
}
