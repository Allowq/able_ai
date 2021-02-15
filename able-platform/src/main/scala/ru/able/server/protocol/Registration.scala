package ru.able.server.protocol

import scala.concurrent.Promise

trait Registration[A, E <: Event[A]] {
  def promise: Promise[E]
}

object Registration {
  case class SingularResponseRegistration[A](promise: Promise[SingularEvent[A]]) extends Registration[A, SingularEvent[A]]
  case class StreamReplyRegistration[A](promise: Promise[StreamEvent[A]]) extends Registration[A, StreamEvent[A]]
}