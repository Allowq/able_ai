package ru.able.communication.viatcp.protocol

import akka.stream.scaladsl.Source
import scala.concurrent.Promise

trait Event[A]

case class SingularEvent[A](data: A) extends Event[A]

case class SingularErrorEvent[A](data: A) extends Event[A]

case class StreamEvent[A](chunks: Source[A, Any]) extends Event[A]

trait Registration[A, E <: Event[A]] {
  def promise: Promise[E]
}