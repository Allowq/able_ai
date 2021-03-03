package ru.able.detector.model

import java.util.UUID

import ru.able.server.model.CanvasFrameSpecial

case class SignedFrame(UUID: UUID, canvasFrameSpecial: CanvasFrameSpecial)
