package ru.able.model

import org.platanios.tensorflow.api.Tensor

case class DetectionOutput(boxes: Tensor, scores: Tensor, classes: Tensor, num: Tensor)
