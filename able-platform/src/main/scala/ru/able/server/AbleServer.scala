package ru.able.server

import java.awt.event.WindowAdapter
import java.awt.{BorderLayout, Dimension, GridLayout}
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.{Timer, TimerTask}

import com.typesafe.scalalogging.LazyLogging
import javax.swing.JFrame.EXIT_ON_CLOSE
import javax.swing.{JLabel, JPanel}
import org.bytedeco.javacpp.avformat
import org.bytedeco.javacv.CanvasFrame
import ru.able.server.camera.{CanvasFrameSpecial, SocketFrame}
import ru.able.util.ObjectInputStreamWithCustomClassLoader

object AbleServer extends App with LazyLogging {

  logger.info(s"Able Platform start up ...")

  avformat.av_register_all()

  val mainPanel               = new JPanel()
  val framesQueueCounterLabel = new JLabel()
  val frameAddedLabel         = new JLabel()
  val fpsCounterLabel         = new JLabel()
  val frameDateLabel          = new JLabel()
  val statusLabel             = new JLabel()

  val server                  = new ServerSocket(9999)
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

  while (running) {
    statusLabel.setText("Server waiting for clients...")
    val serverSocket = server.accept()
    val in           = new ObjectInputStreamWithCustomClassLoader(serverSocket.getInputStream())

    try {
      //      val next = in.readObject().asInstanceOf[Seq[SocketFrame]]
      //      frameAddedLabel.setText(s"Added ${next.size} frames.")
      //      next.map(toCanvasFrameSpecial).foreach(f => frames.put(f))

      val next = in.readObject().asInstanceOf[SocketFrame]
      frameAddedLabel.setText(s"Added 1 frame.")
      frames.put(new CanvasFrameSpecial(next))
    } catch {
      case e: Exception => logger.error(e.getMessage, e)
    } finally {
      serverSocket.close()
    }
  }

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

        //        val file = new File(s"${frame.date.toString}.jpg")
        //        opencv_imgcodecs.imwrite(file.getAbsolutePath, converterMat.convert(frame.frame))
        //        opencv_imgcodecs.cvSaveImage(file.getAbsolutePath, converter.convert(frame.frame))
        //        println(s"file saved to ${file.getAbsolutePath}")

      }
      fpsCounterLabel.setText(s"FPS: ${1000 / refreshRate} / Refresh rate: $refreshRate")
    }
  }
}
