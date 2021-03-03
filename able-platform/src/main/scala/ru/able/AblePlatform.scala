package ru.able

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.avformat
import ru.able.detector.stage.ShowOriginalEventStage
import ru.able.server.ServerBase
import ru.able.server.pipeline.FrameSeqHandler
import ru.able.server.protocol.{Event, MessageFormat, SimpleMessage}

object AblePlatform extends App with LazyLogging {

  System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0")
  System.setProperty("org.bytedeco.javacpp.maxbytes", "0")
  //  System.setProperty("org.bytedeco.javacpp.logger.debug", "true")

  logger.info(s"Able Platform start up ...")

  avformat.av_register_all()

  implicit val actorSystem      = ActorSystem("ServerActorSystem")
  implicit val materializer     = Materializer.createMaterializer(actorSystem)
  implicit val executionContext = materializer.system.dispatcher

//  val showImageStage = new ShowOriginalEventStage[Event[MessageFormat]]()
  ServerBase.applyModern("192.168.0.101", 9999, FrameSeqHandler, SimpleMessage.fullProtocol)

//  val server = new ServerBase(FrameSeqHandler, SimpleMessage.protocol)
//  server.setupConnection("192.168.0.101", 9999)
}
