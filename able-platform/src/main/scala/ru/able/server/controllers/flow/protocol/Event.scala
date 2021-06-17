package ru.able.server.controllers.flow.protocol

import akka.stream.scaladsl.Source

trait Event[A]

case class StreamEvent[A](chunks: Source[A, Any]) extends Event[A]
case class SingularEvent[A](data: A) extends Event[A]
case class SingularErrorEvent[A](data: A) extends Event[A]