package ru.able.camera.motiondetector.stage

import akka.stream._
import akka.stream.stage._
import com.typesafe.scalalogging.LazyLogging
import ru.able.camera.motiondetector.bgsubtractor.BackgroundSubstractor
import ru.able.camera.camera.{CameraFrame, MotionDetectFrame}

class BackgroundSubstractorStage(backgroundSubstractor: BackgroundSubstractor)
    extends GraphStage[FlowShape[CameraFrame, MotionDetectFrame]]
    with LazyLogging {

  private val in  = Inlet[CameraFrame]("MotionDetect.in")
  private val out = Outlet[MotionDetectFrame]("MotionDetect.out")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      private def substractBackground(frame: CameraFrame) = backgroundSubstractor.substractBackground(frame)

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          push(out, substractBackground(grab(in)))
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
    }

  override def shape: FlowShape[CameraFrame, MotionDetectFrame] = FlowShape.of(in, out)
}
