package ru.able.camera.objectdetector

import akka.actor.Actor
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink}
import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacv.CanvasFrame
import ru.able.camera.framereader.model.CameraFrame
import ru.able.camera.framereader.plugin.stage.ShowImageStage
import ru.able.communication.viatcp.EventBus
import ru.able.communication.viatcp.EventBus.LabelMap
import ru.able.router.model.{AdvancedPluginStart, Plugin}

import scala.util.Try

class ObjectDetectorPlugin(canvas: CanvasFrame)(implicit mat: Materializer) extends Plugin with LazyLogging
{
  private var _pluginKillSwitch: Option[SharedKillSwitch] = None

//  val broadcastAndSink = Flow.fromGraph(GraphDSL.create() { implicit b =>
//    import GraphDSL.Implicits._
//
//    val broadcast = b.add(Broadcast[CameraFrame](2))
//  })

  override def start(ps: AdvancedPluginStart): Unit = {
    Try {
      _pluginKillSwitch = Some(KillSwitches.shared("ShowImage"))
      val (broadcast, killSwitch) = (ps.broadcast, ps.ks.sharedKillSwitch)

      broadcast.mat
        .via(killSwitch.flow)
        .via(_pluginKillSwitch.get.flow)
//        .async
//        .via(broadcastAndSink)
        .runWith(new ShowImageStage(canvas))
    } recover {
      case e: Exception => logger.error(e.getMessage, e)
    }
  }

  override def stop(): Unit = _pluginKillSwitch match {
    case Some(ks) => ks.shutdown()
    case None     => logger.error("shutdown")
  }
}
