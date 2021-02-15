package ru.able.client.pipeline

import akka.stream.Materializer
import ru.able.client.protocol.Action

trait Resolver[In] {
  def process(implicit mat: Materializer): PartialFunction[In, Action]
}

