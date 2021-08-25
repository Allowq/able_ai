package ru.able.camera.utils

import java.util.concurrent.Executors
import java.util.function.Supplier

import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacv.{Frame, OpenCVFrameConverter}

import scala.concurrent.ExecutionContext

object MediaConversion {

  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(
    Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors())
  )

  // Each thread gets its own greyMat for safety
  private val frameToMatConverter = ThreadLocal.withInitial(new Supplier[OpenCVFrameConverter.ToMat] {
    def get(): OpenCVFrameConverter.ToMat = new OpenCVFrameConverter.ToMat
  })

  private val frameToIplConverter = ThreadLocal.withInitial(new Supplier[OpenCVFrameConverter.ToIplImage] {
    override def get(): OpenCVFrameConverter.ToIplImage = new OpenCVFrameConverter.ToIplImage
  })

  def toMat(frame: Frame): Mat = frameToMatConverter.get().convert(frame)

  def toFrame(mat: Mat): Frame = frameToMatConverter.get().convert(mat)

  def toIplImage(frame: Frame): IplImage = frameToIplConverter.get().convert(frame)

  def toIplImage(mat: Mat): IplImage = toIplImage(toFrame(mat))
}