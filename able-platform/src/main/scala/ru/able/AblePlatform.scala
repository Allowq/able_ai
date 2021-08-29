package ru.able

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.avformat
import ru.able.server.ServerBase
import ru.able.services.detector.util.ModelLoader

import scala.util.{Failure, Success}

object AblePlatform extends App with LazyLogging {

  System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0")
  System.setProperty("org.bytedeco.javacpp.maxbytes", "0")
  //  System.setProperty("org.bytedeco.javacpp.logger.debug", "true")

  logger.info(s"Able Platform start up ...")

  avformat.av_register_all()

  implicit val actorSystem      = ActorSystem("ServerActorSystem")
  implicit val materializer     = Materializer.createMaterializer(actorSystem)
  implicit val executionContext = materializer.system.dispatcher

  ModelLoader.initializeModel.onComplete {
    case Success(_) => new ServerBase("127.0.0.1", 9999)
    case Failure(_) =>
  }
}
