package ru.able.server.controllers.flow.model

sealed trait ResolverType
case object BasicRT extends ResolverType
case object ExtendedRT extends ResolverType
case object FrameSeqRT extends ResolverType
