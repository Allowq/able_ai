package ru.able.camera.utils

import java.util.concurrent.Executors
import java.util.function.Supplier

import akka.actor.ActorSystem
import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacv.{Frame, OpenCVFrameConverter}

import scala.concurrent.{ExecutionContext, Future}

object MediaConversion {

  private implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  // Each thread gets its own greyMat for safety
  private val frameToMatConverter = ThreadLocal.withInitial(new Supplier[OpenCVFrameConverter.ToMat] {
    def get(): OpenCVFrameConverter.ToMat = new OpenCVFrameConverter.ToMat
  })

  private val frameToIplConverter = ThreadLocal.withInitial(new Supplier[OpenCVFrameConverter.ToIplImage] {
    override def get(): OpenCVFrameConverter.ToIplImage = new OpenCVFrameConverter.ToIplImage
  })

  /**
   * Returns an OpenCV Mat for a given JavaCV frame
   */
  def toMat(frame: Frame): Mat = frameToMatConverter.get().convert(frame)
  def toAsyncMat(frame: Frame)(implicit system: ActorSystem): Future[Mat] =
    Future { frameToMatConverter.get().convert(frame) }

  /**
   * Returns a JavaCV Frame for a given OpenCV Mat
   */
  def toFrame(mat: Mat): Frame = frameToMatConverter.get().convert(mat)
  def toAsyncFrame(mat: Mat)(implicit system: ActorSystem): Future[Frame] =
    Future { frameToMatConverter.get().convert(mat) }

  def toIplImage(frame: Frame): IplImage = frameToIplConverter.get().convert(frame)
  def toAsyncIplImage(frame: Frame)(implicit system: ActorSystem): Future[IplImage] =
    Future { frameToIplConverter.get().convert(frame) }

  def toIplImage(mat: Mat): IplImage ={
    val t = frameToIplConverter.get()
    t.convert(t.convert(mat))
  }

  def toAsyncIplImage(mat: Mat)(implicit system: ActorSystem): Future[IplImage] =
    Future {
      val t = frameToIplConverter.get()
      t.convert(t.convert(mat))
    }

  /**
   * Clones the image and returns a flipped version of the given image matrix along the y axis (horizontally)
   */
  def horizontal(mat: Mat): Mat = {
    val cloned = mat.clone()
    flip(cloned, cloned, 1)
    cloned
  }
  def asyncHorizontal(mat: Mat)(implicit system: ActorSystem): Future[Mat] = Future {
    val cloned = mat.clone()
    flip(cloned, cloned, 1)
    cloned
  }

}