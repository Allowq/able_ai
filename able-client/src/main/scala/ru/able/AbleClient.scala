package ru.able

import java.awt.event.WindowAdapter

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.google.inject.Key
import com.google.inject.name.Names
import com.typesafe.scalalogging.LazyLogging
import javax.swing.JFrame.EXIT_ON_CLOSE
import org.bytedeco.javacv.CanvasFrame
import ru.able.camera.motiondetector.bgsubtractor.GaussianMixtureBackgroundSubstractor
import ru.able.camera.motiondetector.plugin.{MotionDetectorPlugin, StreamerPlugin}
import ru.able.router.Orchestrator
import ru.able.camera.framereader.plugin.ShowImagePlugin
import ru.able.communication.viatcp.TCPEventBus
import ru.able.system.module.ModuleInjector

object AbleClient extends App with LazyLogging {

  System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0")
  System.setProperty("org.bytedeco.javacpp.maxbytes", "0")
//  System.setProperty("org.bytedeco.javacpp.logger.debug", "true")

  logger.info(s"AbleClient start up ...")

  private implicit val actorSystem  = ActorSystem()
  private implicit val materializer = Materializer.createMaterializer(actorSystem)
  private implicit val executionContext = materializer.system.dispatchers.defaultGlobalDispatcher

  private val modules               = new ModuleInjector(actorSystem, materializer)
  private val orchestrator          = modules.injector.getInstance(classOf[Orchestrator])
  private val backgroundSubstractor = modules.injector.getInstance(classOf[GaussianMixtureBackgroundSubstractor])
  private val communicationProvider = modules.injector.getInstance(Key.get(classOf[ActorRef], Names.named("ReactiveTCPBridge")))
  private val eventBus              = modules.injector.getInstance(classOf[TCPEventBus])

//  private val notifier              = modules.injector.getInstance(Key.get(classOf[ActorRef], Names.named("Notifier")))

  lazy val shutdown: Unit = {
    logger.info(s"AbleClient shutdown.")
    stopStreaming(orchestrator)
  }

  val canvas = createCanvas(shutdown)

  val showImagePlugin = new ShowImagePlugin(canvas)
//  val streamerPlugin = new StreamerPlugin(notifier)(materializer)
//  val streamerPlugin = new StreamerPlugin(networkClient)(materializer)
//  val motionDetect = new MotionDetectorPlugin(null, backgroundSubstractor, notifier)
  val motionDetect = new MotionDetectorPlugin(backgroundSubstractor, communicationProvider)

//  orchestator.addPlugin(streamerPlugin)
  orchestrator.addPlugin(showImagePlugin)
  orchestrator.addPlugin(motionDetect)

  startStreaming(orchestrator)

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
    val canvas = new CanvasFrame("Able Client")
    // TODO: Remove it and upgrade to graceful shutdown
    canvas.setDefaultCloseOperation(EXIT_ON_CLOSE)
    canvas.addWindowListener(new WindowAdapter() {
      override def windowClosing(windowEvent: java.awt.event.WindowEvent): Unit = shutdown
    })

    canvas
  }
}
