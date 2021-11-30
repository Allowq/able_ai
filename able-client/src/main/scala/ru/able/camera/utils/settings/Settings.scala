package ru.able.camera.utils.settings

import java.util.UUID
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Try

sealed trait Settings {

  /**
    * Camera path on the OS.
    *
    * @return
    */
  def cameraPath(): String

  /**
    * Video format that is used by FrameGrabber.
    * Although it's not tied to ffmpeg, it is the default FrameGrabber.
    * Depending on OS (for ffmpeg it's dshow on Windows, video4linux on Linux).
    *
    * @see https://ffmpeg.org/ffmpeg-devices.html
    * @return
    */
  def cameraFormat(): String

  /**
    * Additional options for the FrameGrabber.
    *
    * @return key value pairs
    */
  def cameraOptions(): Map[String, AnyRef]

  def clientUUID: UUID

  /**
    * Options for BackgroundSubstractorMog2.
    *
    * @return key value pairs
    */
  def motionDetectOptions(): Map[String, AnyRef]

  def startupTimeoutDuration(path: String, unit: TimeUnit = TimeUnit.SECONDS): FiniteDuration

  def getInt(path: String): Int

  def networkClientHostname: String

  def networkClientPort: Int

  def maxConnectionsPerHost: Int

  def maxFailuresPerHost: Int

  def failureRecoveryPeriod: FiniteDuration

  def inputBufferSize: Int

  def clientParallelismValue: Int

  def producerParallelismValue: Int
}

class PropertyBasedSettings(config: Config) extends Settings {

  override def getInt(path: String): Int = config.getInt(path)

  override def cameraPath(): String = config.getString("camera.path")

  override def cameraFormat(): String = config.getString("camera.ffmpeg.format")

  override def cameraOptions(): Map[String, AnyRef] = getOptionsMap("camera.options")

  override def clientUUID: UUID = UUID.fromString(config.getString("NetworkConfig.client.uuid"))

  override def motionDetectOptions(): Map[String, AnyRef] = getOptionsMap("motionDetect")

  override def startupTimeoutDuration(path: String, unit: TimeUnit): FiniteDuration =
    nonNull(FiniteDuration(config.getDuration(path, unit), unit), path)

  override def networkClientHostname: String = config.getString("NetworkConfig.client.host.address")

  override def networkClientPort: Int = config.getInt("NetworkConfig.client.host.port")

  override def maxConnectionsPerHost: Int = config.getInt("NetworkConfig.client.host.max-connections")

  override def maxFailuresPerHost: Int = config.getInt("NetworkConfig.client.host.max-failures")

  override def failureRecoveryPeriod: FiniteDuration = Duration(
    config.getDuration("NetworkConfig.client.host.failure-recovery-duration").toNanos,
    TimeUnit.NANOSECONDS
  )

  override def inputBufferSize: Int = config.getInt("NetworkConfig.client.input-buffer-size")

  override def clientParallelismValue: Int = config.getInt("NetworkConfig.client.parallelism")

  override def producerParallelismValue: Int = config.getInt("NetworkConfig.pipeline.parallelism")

  private def getOptionsMap(path: String): Map[String, AnyRef] =
    options(path)
      .map(f => f.unwrapped.asScala.toMap)
      .getOrElse(Map.empty)

  private def options(path: String) =
    Try(Some(config.getObject(path))).recover { case _ => None }.get

  private def nonNull[T](value: T, path: String) = Option(value) match {
    case Some(v) => v
    case None    => throw new RuntimeException(s"Configuration $path is missing!")
  }
}
