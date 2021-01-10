package ru.able.client

import java.awt.event.WindowAdapter

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.google.inject.Key
import com.google.inject.name.Names
import com.typesafe.scalalogging.LazyLogging
import javax.swing.JFrame.EXIT_ON_CLOSE
import org.bytedeco.javacv.{CanvasFrame, OpenCVFrameConverter}
import ru.able.app.Orchestrator
import ru.able.camera.motiondetector.bgsubtractor.GaussianMixtureBasedBackgroundSubstractor
import ru.able.camera.motiondetector.plugin.{MotionDetectorPlugin, StreamerPlugin}
import ru.able.plugin.util.ShowImage
import ru.able.system.module.ModuleInjector

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object AbleClient extends App with LazyLogging {

  System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0")
  System.setProperty("org.bytedeco.javacpp.maxbytes", "0")
//  System.setProperty("org.bytedeco.javacpp.logger.debug", "true")

  logger.info(s"AbleClient start up ...")

  private implicit val actorSystem  = ActorSystem()
  private implicit val materializer = Materializer.createMaterializer(actorSystem)
  private implicit val executionContext = materializer.system.dispatchers.defaultGlobalDispatcher

  private val modules               = new ModuleInjector(actorSystem, materializer)
  private val orchestator           = modules.injector.getInstance(classOf[Orchestrator])
  private val backgroundSubstractor = modules.injector.getInstance(classOf[GaussianMixtureBasedBackgroundSubstractor])
  private val notifier              = modules.injector.getInstance(Key.get(classOf[ActorRef], Names.named("Notifier")))

  lazy val shutdown: Unit = {
    logger.info(s"AbleClient shutdown.")
    stopStreaming(orchestator)
    materializer.shutdown()
  }

  val canvas = createCanvas(shutdown)

//  sleep(6000) // nice :(

//  val streamerPlugin = new StreamerPlugin(notifier)(materializer)
  val showImagePlugin = new ShowImage(canvas,"normal")(materializer)
  val motionDetect = new MotionDetectorPlugin(null, backgroundSubstractor, "motion", notifier)(materializer)

//  orchestator.addPlugin(streamerPlugin)
  orchestator.addPlugin(showImagePlugin)
  orchestator.addPlugin(motionDetect)

  startStreaming(orchestator)

  sys.addShutdownHook(shutdown)

  private def startStreaming(orchestrator: Orchestrator) = {
    orchestrator.start()
    logger.info("Video streaming started.")
  }

  private def stopStreaming(orchestrator: Orchestrator) = {
    logger.info("Shutdown video stream ...")
    orchestrator.stop()
    logger.info("Video streaming stopped.")
  }

  private def createCanvas(shutdown: => Unit): CanvasFrame = {
    val canvas = new CanvasFrame("Able Client View Util")
    canvas.setDefaultCloseOperation(EXIT_ON_CLOSE)
    canvas.addWindowListener(new WindowAdapter() {
      override def windowClosing(windowEvent: java.awt.event.WindowEvent): Unit = {
        logger.debug("Canvas close")
        shutdown
      }
    })
    canvas
  }

  def sleep(ms: Int) = {
    Await.ready(Future {
      Thread.sleep(ms - 10)
    }, ms millisecond)
  }
}
