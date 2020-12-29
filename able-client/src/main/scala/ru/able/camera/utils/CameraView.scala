package ru.able.camera.utils

import java.awt.event.WindowAdapter
import javax.swing.JFrame.EXIT_ON_CLOSE

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.stream.{Materializer, SharedKillSwitch}
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacv.{CanvasFrame, OpenCVFrameConverter}
import org.bytedeco.javacv.OpenCVFrameConverter.ToIplImage
import ru.able.app.Orchestator
import ru.able.camera.camera.stage.ShowImageStage
import ru.able.plugin.util.ShowImage
import ru.able.router.messages.{Error, PluginStart}
import ru.able.system.module.ModuleInjector

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

object CameraView extends App with LazyLogging {

  logger.info(s"Sentinel camera view start up ...")

  private implicit val system = ActorSystem()
  private implicit val materializer =  Materializer.createMaterializer(system)

  private val materializer1 = Materializer.createMaterializer(system)

  private val materializer2 = Materializer.createMaterializer(system)

  private val materializer3 = Materializer.createMaterializer(system)

  private implicit val executionContext =
    materializer.system.dispatchers.defaultGlobalDispatcher
  private val modules = new ModuleInjector(system, materializer)
  private val buncher = modules.injector.getInstance(classOf[Orchestator])

  lazy val shutdown: Unit = {
    logger.info(s"Sentinel camera view shutdown.")
    stopStreaming(buncher)
    materializer.shutdown()
  }

  val converter = () => new OpenCVFrameConverter.ToIplImage()
  val canvas    = createCanvas(shutdown)
  val canvas2   = createCanvas(shutdown)
  val canvas3   = createCanvas(shutdown)
  val canvas4   = createCanvas(shutdown)
  val canvas5   = createCanvas(shutdown)
  val canvas6   = createCanvas(shutdown)

  startStreaming(buncher)

  sleep(4000)

  val showImagePlugin  = new ShowImage(canvas, converter())(materializer)
  val showImagePlugin2 = new ShowImage(canvas2, converter())(materializer)
  val showImagePlugin3 = new ShowImage(canvas3, converter())(materializer)
  val showImagePlugin4 = new ShowImage(canvas4, converter())(materializer)
  val showImagePlugin5 = new ShowImage(canvas5, converter())(materializer)
  val showImagePlugin6 = new ShowImage(canvas6, converter())(materializer)

  buncher.addPlugin(showImagePlugin)
  buncher.addPlugin(showImagePlugin2)
  buncher.addPlugin(showImagePlugin3)
  buncher.addPlugin(showImagePlugin4)
  buncher.addPlugin(showImagePlugin5)
  buncher.addPlugin(showImagePlugin6)

  sys.addShutdownHook(shutdown)

  private def startStreaming(buncher: Orchestator) = {
    val start = buncher.start()

    logger.info("Video streaming started.")
  }

  private def stopStreaming(buncher: Orchestator) = {
    logger.info("Shutdown video stream ...")
    val stop = buncher.stop()

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

  def sleep(ms: Int) = {
    Await.ready(Future {
      Thread.sleep(ms - 1)
    }, ms millisecond)
  }
  @deprecated
  class ShowImageActor(canvas: CanvasFrame, converter: ToIplImage) extends Actor with ActorLogging with LazyLogging {
    override def receive: Receive = {
      case PluginStart(killSwitch, broadcast) =>
        val requestor = sender()
        Try({

          val publisher = broadcast.graph.run()
          publisher
            .via(killSwitch.asInstanceOf[SharedKillSwitch].flow)
            .runWith(new ShowImageStage(canvas, converter))

        }) recover {
          case e: Exception => requestor ! Error(e.getMessage)
        }
    }
  }
}
