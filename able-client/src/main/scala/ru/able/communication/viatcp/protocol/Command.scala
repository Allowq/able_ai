package ru.able.communication.viatcp.protocol

import akka.stream.scaladsl.Source

trait Command[Out]

case class SingularCommand[Out](payload: Out) extends Command[Out]
case class StreamingCommand[Out](stream: Source[Out, Any]) extends Command[Out]