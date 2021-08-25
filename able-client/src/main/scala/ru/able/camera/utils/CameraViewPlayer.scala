package ru.able.camera.utils

import java.awt.event.WindowAdapter

import javax.swing.JFrame.EXIT_ON_CLOSE
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacv.CanvasFrame
import ru.able.router.Orchestrator
import ru.able.camera.framereader.plugin.ShowImagePlugin
import ru.able.system.module.ModuleInjector

import scala.concurrent.ExecutionContext

object CameraViewPlayer extends App with LazyLogging {
  private implicit val _system: ActorSystem                 = ActorSystem()
  private implicit val _materializer: Materializer          = Materializer.createMaterializer(_system)
  private implicit val _executionContext: ExecutionContext  = _materializer.system.dispatcher

  private val _modules      = new ModuleInjector(_system, _materializer)
  private val _orchestrator = _modules.injector.getInstance(classOf[Orchestrator])

  lazy val shutdown: Unit = {
    logger.info(s"Sentinel camera view shutdown.")
    stopStreaming(_orchestrator)
    _materializer.shutdown()
  }

  val canvas  = createCanvas(shutdown)
  val canvas2 = createCanvas(shutdown)
  val canvas3 = createCanvas(shutdown)
  val canvas4 = createCanvas(shutdown)
  val canvas5 = createCanvas(shutdown)
  val canvas6 = createCanvas(shutdown)

  startStreaming(_orchestrator)

  val showImagePlugin  = new ShowImagePlugin(canvas)
  val showImagePlugin2 = new ShowImagePlugin(canvas2)
  val showImagePlugin3 = new ShowImagePlugin(canvas3)
  val showImagePlugin4 = new ShowImagePlugin(canvas4)
  val showImagePlugin5 = new ShowImagePlugin(canvas5)
  val showImagePlugin6 = new ShowImagePlugin(canvas6)

  _orchestrator.addPlugin(showImagePlugin)
  _orchestrator.addPlugin(showImagePlugin2)
  _orchestrator.addPlugin(showImagePlugin3)
  _orchestrator.addPlugin(showImagePlugin4)
  _orchestrator.addPlugin(showImagePlugin5)
  _orchestrator.addPlugin(showImagePlugin6)

  sys.addShutdownHook(shutdown)

  private def startStreaming(orchestrator: Orchestrator): Unit = {
    val start = orchestrator.start()

    logger.info("Video streaming started.")
  }

  private def stopStreaming(orchestrator: Orchestrator): Unit = {
    logger.info("Shutdown video stream ...")
    val stop = orchestrator.stop()

    logger.info("Video streaming stopped.")
  }

  private def createCanvas(shutdown: => Unit): CanvasFrame = {
    val canvas = new CanvasFrame("Sentinel Camera View Util")
    canvas.setDefaultCloseOperation(EXIT_ON_CLOSE)
    canvas.addWindowListener(new WindowAdapter() {
      override def windowClosing(windowEvent: java.awt.event.WindowEvent): Unit = {
        logger.debug("Canvas close")
        shutdown
      }
    })
    canvas
  }
}
