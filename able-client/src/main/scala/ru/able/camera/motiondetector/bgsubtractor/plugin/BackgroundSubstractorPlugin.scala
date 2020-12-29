package ru.able.camera.motiondetector.bgsubtractor.plugin

import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import com.typesafe.scalalogging.LazyLogging
import ru.able.camera.camera.stage.ShowImageStage
import ru.able.camera.motiondetector.bgsubtractor.BackgroundSubstractor
import ru.able.camera.motiondetector.stage.BackgroundSubstractorStage
import ru.able.plugin.Plugin
import ru.able.router.messages.{AdvancedPluginStart, PluginStart}

import scala.util.Try

class BackgroundSubstractorPlugin(backgroundSubstractor: BackgroundSubstractor) (implicit mat: Materializer)
  extends Plugin
    with LazyLogging {
  var pluginKillSwitch: Option[SharedKillSwitch] = None

  override def start(ps: AdvancedPluginStart): Unit =
    Try({

      pluginKillSwitch = Some(KillSwitches.shared("ShowImage"))

      logger.info("Starting image view")

      val (broadcast, killSwitch) = (ps.broadcast, ps.ks.sharedKillSwitch)

      logger.info(broadcast.toString)

      val publisher               = broadcast.mat

      publisher
        .via(killSwitch.flow)
        .via(pluginKillSwitch.get.flow)
        .via(new BackgroundSubstractorStage(backgroundSubstractor))

    }) recover {
      case e: Exception => logger.error(e.getMessage, e)
    }

  override def stop(): Unit = pluginKillSwitch match {
    case Some(ks) => ks.shutdown()
    case None     => logger.error("shutdown")
  }
}
