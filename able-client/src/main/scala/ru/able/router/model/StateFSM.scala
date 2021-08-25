package ru.able.router.model

sealed trait StateFSM

case object Idle extends StateFSM
case object Waiting extends StateFSM
case object Active extends StateFSM