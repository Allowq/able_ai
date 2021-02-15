package ru.able.detector

import java.awt.{BorderLayout, Dimension, GridLayout}
import java.awt.event.WindowAdapter

import javax.swing.JFrame.EXIT_ON_CLOSE
import javax.swing.{JLabel, JPanel}
import java.util.{Timer, TimerTask}
import java.util.concurrent.LinkedBlockingQueue

import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacv.CanvasFrame
import ru.able.server.model.CanvasFrameSpecial
import ru.able.server.pipeline.Resolver
import ru.able.server.protocol.{Action, ConsumerAction, FrameSeqMessage, MessageFormat}

class ViewResolver extends Resolver[MessageFormat] with LazyLogging {
  val mainPanel               = new JPanel()
  val framesQueueCounterLabel = new JLabel()
  val frameAddedLabel         = new JLabel()
  val fpsCounterLabel         = new JLabel()
  val frameDateLabel          = new JLabel()
  val statusLabel             = new JLabel()

  val frames                  = new LinkedBlockingQueue[CanvasFrameSpecial]()
  var running                 = true

  private val refreshRate     = 200
  val timerTask = new UiUpdateTask()

  val timer = new Timer(true)
  timer.scheduleAtFixedRate(timerTask, 0, refreshRate)

  val canvas: CanvasFrame = createCanvas({
    running = false
    timer.cancel()
    System.exit(0)
  })

  override def process(implicit mat: Materializer): PartialFunction[MessageFormat, Action] = {
    case FrameSeqMessage(socketFrames) =>
      socketFrames.foreach { sf =>
        println(sf.date.toString)
        frames.put(new CanvasFrameSpecial(sf))
      }
      ConsumerAction.Ignore
    case x => println("Unhandled: " + x); ConsumerAction.Ignore
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
      }
      fpsCounterLabel.setText(s"FPS: ${1000 / refreshRate} / Refresh rate: $refreshRate")
    }
  }
}
