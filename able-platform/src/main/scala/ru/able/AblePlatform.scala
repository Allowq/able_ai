package ru.able

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import org.bytedeco.javacpp.avformat
import ru.able.detector.ViewResolver
import ru.able.server.ServerBase
import ru.able.server.protocol.SimpleMessage

object AblePlatform extends App with LazyLogging {

  logger.info(s"Able Platform start up ...")

  avformat.av_register_all()

  implicit val actorSystem      = ActorSystem("ServerActorSystem")
  implicit val materializer     = Materializer.createMaterializer(actorSystem)
  implicit val executionContext = materializer.system.dispatcher

  val viewResolver = new ViewResolver()
  ServerBase("192.168.0.101", 9999, viewResolver, SimpleMessage.protocol.reversed)
}
