package ru.able

import java.awt.event.WindowAdapter
import java.awt.{BorderLayout, Dimension, GridLayout}
import java.util.concurrent.LinkedBlockingQueue
import java.util.{Timer, TimerTask}

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import javax.swing.JFrame.EXIT_ON_CLOSE
import javax.swing.{JLabel, JPanel}
import org.bytedeco.javacpp.avformat
import org.bytedeco.javacv.CanvasFrame
import ru.able.server.ServerBase
import ru.able.server.pipeline.FrameSeqHandler
import ru.able.server.model.{CanvasFrameSpecial, SocketFrame}
import ru.able.server.protocol.SimpleMessage

object AblePlatform extends App with LazyLogging {

  logger.info(s"Able Platform start up ...")

  avformat.av_register_all()

  val mainPanel               = new JPanel()
  val framesQueueCounterLabel = new JLabel()
  val frameAddedLabel         = new JLabel()
  val fpsCounterLabel         = new JLabel()
  val frameDateLabel          = new JLabel()
  val statusLabel             = new JLabel()

  implicit val actorSystem      = ActorSystem("ServerActorSystem")
  implicit val materializer     = Materializer.createMaterializer(actorSystem)
  implicit val executionContext = materializer.system.dispatcher

  val server                  = ServerBase("192.168.0.101", 9999, FrameSeqHandler, SimpleMessage.protocol.reversed)
  var running                 = true
  val frames                  = new LinkedBlockingQueue[CanvasFrameSpecial]()

  private val refreshRate     = 200
  val timerTask = new UiUpdateTask()

  val timer = new Timer(true)
  timer.scheduleAtFixedRate(timerTask, 0, refreshRate)

  val canvas: CanvasFrame = createCanvas({
    running = false
    timer.cancel()
    System.exit(0)
  })

//  while (running) {
//    statusLabel.setText("Server waiting for clients...")
//    val serverSocket = server.accept()
//    val in           = new ObjectInputStreamWithCustomClassLoader(serverSocket.getInputStream())

//    try {
//      val next = in.readObject().asInstanceOf[Seq[SocketFrame]]
//      frameAddedLabel.setText(s"Added ${next.size} frames.")
//      next.foreach(
//        f => frames.put(new CanvasFrameSpecial(f))
//      )
//    } catch {
//      case e: Exception => logger.error(e.getMessage, e)
//    } finally {
//      serverSocket.close()
//    }
//  }

  private def createCanvas(shutdown: => Unit): CanvasFrame = {
    val canvas = new CanvasFrame("Able Platform")
    canvas.setDefaultCloseOperation(EXIT_ON_CLOSE)
    canvas.setLayout(new GridLayout(1, 0))
    canvas.getContentPane().add(mainPanel, BorderLayout.CENTER)
    canvas.setSize(new Dimension(640, 480))
    canvas.setVisible(true)
    val layout = new GridLayout(0, 1)
    mainPanel.setLayout(layout)
    mainPanel.add(framesQueueCounterLabel)
    mainPanel.add(frameAddedLabel)
    mainPanel.add(fpsCounterLabel)
    mainPanel.add(frameDateLabel)
    mainPanel.add(statusLabel)
    canvas.addWindowListener(new WindowAdapter() {
      override def windowClosing(windowEvent: java.awt.event.WindowEvent): Unit = {
        logger.debug("Canvas close")
        shutdown
      }
    })
    canvas
  }

  class UiUpdateTask extends TimerTask {
    override def run(): Unit = {
      framesQueueCounterLabel.setText(s"Frames: ${frames.size()}")
      if (!frames.isEmpty) {
        val frame = frames.take()
        frameDateLabel.setText(s"Frame date: ${frame.date.toString}")
        canvas.showImage(frame.frame)
      }
      fpsCounterLabel.setText(s"FPS: ${1000 / refreshRate} / Refresh rate: $refreshRate")
    }
  }
}
