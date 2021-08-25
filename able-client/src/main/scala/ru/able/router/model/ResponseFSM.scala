package ru.able.router.model

sealed trait ResponseFSM

case class Ready(msg: String) extends ResponseFSM
case class Error(reason: String) extends ResponseFSM
case class Status(reason: Either[Throwable, String]) extends ResponseFSM
