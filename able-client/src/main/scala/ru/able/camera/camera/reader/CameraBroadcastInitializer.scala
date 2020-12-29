package ru.able.camera.camera.reader

import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import ru.able.camera.camera.reader.KillSwitches.GlobalKillSwitch

import scala.concurrent.Promise

class CameraBroadcastInitializer @Inject()(
    broadCastMateralizer: BroadcastMaterializer)
    extends LazyLogging {

  def create(gks: GlobalKillSwitch): Promise[BroadCastRunnableGraph] =
    broadCastMateralizer.create(gks)

}
