package ru.able.detector.view

import java.awt.event.WindowAdapter
import java.awt.{BorderLayout, Dimension, GridLayout}
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import javax.swing.JFrame.EXIT_ON_CLOSE
import javax.swing.{JLabel, JPanel}
import org.bytedeco.javacv.CanvasFrame
import ru.able.server.model.{CanvasFrameSpecial, SocketFrame}

class CanvasDetector extends LazyLogging {
  private val _mainPanel               = new JPanel()
  private val _framesCounterLabel      = new JLabel()
  private val _frameDateLabel          = new JLabel()
  private val _statusLabel             = new JLabel()
  private var _framesCounter           = 0

  val canvas: CanvasFrame = createCanvas({
    //TODO: Upgrade it to graceful shutdown (via killswitch or etc)
    System.exit(0)
  })

  def updateCanvas(uuid: UUID, cf: CanvasFrameSpecial): Unit = {
    _framesCounter += 1
    _framesCounterLabel.setText(s"Frames: ${_framesCounter}")
    _frameDateLabel.setText(s"Frame date: ${cf.date.toString}")
    _statusLabel.setText(s"Client UUID: ${uuid.toString}")
    canvas.showImage(cf.frame)
  }

  def updateCanvas(uuid: UUID, socketFrames: Seq[SocketFrame]): Unit = {
    socketFrames.foreach { sf =>
      _framesCounter += 1
      _framesCounterLabel.setText(s"Frames: ${_framesCounter}")
      _frameDateLabel.setText(s"Frame date: ${sf.date.toString}")
      _statusLabel.setText(s"Client UUID: ${uuid.toString}")
      canvas.showImage(CanvasFrameSpecial(sf).frame)
    }
  }

  private def createCanvas(shutdown: => Unit): CanvasFrame = {
    val canvas = new CanvasFrame("Able Platform")

    canvas.setDefaultCloseOperation(EXIT_ON_CLOSE)
    canvas.setLayout(new GridLayout(1, 0))
    canvas.getContentPane().add(_mainPanel, BorderLayout.CENTER)
    canvas.setSize(new Dimension(640, 480))
    canvas.setVisible(true)

    val layout = new GridLayout(0, 1)
    _mainPanel.setLayout(layout)
    _mainPanel.add(_framesCounterLabel)
    _mainPanel.add(_frameDateLabel)
    _mainPanel.add(_statusLabel)

    canvas.addWindowListener(new WindowAdapter() {
      override def windowClosing(windowEvent: java.awt.event.WindowEvent): Unit = {
        logger.debug("Canvas close")
        shutdown
      }
    })
    canvas
  }
}
