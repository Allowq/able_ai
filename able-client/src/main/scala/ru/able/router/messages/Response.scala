package ru.able.router.messages

sealed trait Response

case class Ready(msg: String) extends Response
case class Error(reason: String) extends Response
case class Status(reason: Either[Throwable, String]) extends Response
