// *****************************************************************************
// Main Info
// *****************************************************************************

lazy val root =
  project
    .in(file("."))
    .settings(settings)
    .settings(
      crossPaths := false,
      autoScalaLibrary := false,
      mainClass in Compile := (mainClass in Compile in tensorFlow).value,
      assemblySettings
    )
    .aggregate(tensorFlow)

lazy val dl4j =
  project
    .in(file("dl4j"))
    .settings(settings)
    .settings(
      name := "dl4j",
      scalaVersion := "2.11.12", // ScalNet and ND4S are only available for Scala 2.11
      libraryDependencies ++= Seq(
        library.dl4j,
        library.dl4jCuda,
        library.dl4jUi,
        library.logbackClassic,
        library.nd4jNativePlatform,
        library.scalNet
      )
    )

lazy val tensorFlow =
  project
    .in(file("tensorflow"))
    .settings(settings)
    .settings(
      name := "tensorflow",
      PB.targets in Compile := Seq(
        scalapb.gen() -> (sourceManaged in Compile).value
      ),
      javaCppPresetLibs ++= Seq(
        "ffmpeg" -> "3.4.1"
      ),
      libraryDependencies ++= Seq(
        library.betterFiles,
        library.janino,
        library.logbackClassic,
        library.tensorFlow,
        library.tensorFlowData,
        library.protobufScala,
        library.akkaScala
      ),
      mainClass in (Compile, run) := Some("ru.able.Main"),
      mainClass in assembly := Some("ru.able.Main"),
      resourceDirectory in Compile := file(".") / ".src/main/resources",
      resourceDirectory in Runtime := file(".") / ".src/main/resources",
      fork := true, // prevent classloader issues caused by sbt and opencv
      assemblySettings
    )

// The MXNet example has been moved into its own sbt project for now because we have to build mxnet manually,
// and we don't want to break dependency resolution for the other projects.
// lazy val mxnet = project

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val betterFiles =     "3.4.0"
      val dl4j =            "1.0.0-alpha"
      val janino =          "2.6.1"
      val logbackClassic =  "1.2.3"
      val scalaCheck =      "1.13.5"
      val scalaTest  =      "3.0.4"
      val tensorFlow =      "0.2.4"
      val protobufVersion = "0.7.4"
      val akkaVersion =     "2.6.10"
    }
    val betterFiles =         "com.github.pathikrit"  %% "better-files"           % Version.betterFiles
    val dl4j =                "org.deeplearning4j"    % "deeplearning4j-core"     % Version.dl4j
    val dl4jUi =              "org.deeplearning4j"    %% "deeplearning4j-ui"      % Version.dl4j
    val janino =              "org.codehaus.janino"   % "janino"                  % Version.janino
    val logbackClassic =      "ch.qos.logback"        % "logback-classic"         % Version.logbackClassic
    val nd4jNativePlatform =  "org.nd4j"              % "nd4j-cuda-9.0-platform"  % Version.dl4j
    val dl4jCuda =            "org.deeplearning4j"    % "deeplearning4j-cuda-9.0" % Version.dl4j
    val scalaCheck =          "org.scalacheck"        %% "scalacheck"             % Version.scalaCheck
    val scalaTest  =          "org.scalatest"         %% "scalatest"              % Version.scalaTest
    val scalNet =             "org.deeplearning4j"    %% "scalnet"                % Version.dl4j
    val tensorFlow =          "org.platanios"         %% "tensorflow"             % Version.tensorFlow classifier tensorflow_classifier
    val tensorFlowData =      "org.platanios"         %% "tensorflow-data"        % Version.tensorFlow
    val protobufScala =       "com.thesamet.scalapb"  %% "scalapb-runtime"        % Version.protobufVersion % "protobuf"
    val akkaScala =           "com.typesafe.akka"     %% "akka-stream"            % Version.akkaVersion
  }

// *****************************************************************************
// Assembly settings
// *****************************************************************************

import sbtassembly.AssemblyPlugin.defaultShellScript

lazy val assemblySettings = Seq(
  assemblyOption in assembly := (assemblyOption in assembly).value.copy(cacheUnzip = false),
  assemblyOption in assembly := (assemblyOption in assembly).value.copy(cacheOutput = false),
  assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript = Some(defaultShellScript)),
  assemblyJarName in assembly := s"${name.value}-${version.value}.jar",

  assemblyMergeStrategy in assembly := {
    case x if Assembly.isConfigFile(x) =>
      MergeStrategy.concat
    case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
      MergeStrategy.rename
    case PathList("META-INF", xs @ _*) =>
      (xs map {_.toLowerCase}) match {
        case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
          MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
          MergeStrategy.discard
        case "plexus" :: xs =>
          MergeStrategy.discard
        case "services" :: xs =>
          MergeStrategy.filterDistinctLines
        case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
          MergeStrategy.filterDistinctLines
        case _ => MergeStrategy.deduplicate
      }
    case _ => MergeStrategy.deduplicate
  }
)

// *****************************************************************************
// Common settings
// *****************************************************************************

lazy val settings = commonSettings

lazy val commonSettings = Seq(
  name := "able-ai-platform",
  organization := "ru.able",
  organizationName := "Able AI",
  developers := List(Developer("allowq", "Nikita Kupriyanov", "", url("https://github.com/allowq"))),
  licenses += ("GNU Lesser GPL 3.0", url("https://www.gnu.org/licenses/lgpl-3.0.html")),
  startYear := Some(2020),

  scalaVersion := "2.12.6",
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-language:_",
    "-target:jvm-1.8",
    "-encoding", "UTF-8"
  ),
  exportJars := true,
  autoAPIMappings := true,
  parallelExecution := false,
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  )
)

// *****************************************************************************
// Decide platform
// *****************************************************************************

lazy val platform: String = {
  // Determine platform name using code similar to javacpp
  // com.googlecode.javacpp.Loader.java line 60-84
  val jvmName = System.getProperty("java.vm.name").toLowerCase
  var osName  = System.getProperty("os.name").toLowerCase
  var osArch  = System.getProperty("os.arch").toLowerCase
  if (jvmName.startsWith("dalvik") && osName.startsWith("linux")) {
    osName = "android"
  } else if (jvmName.startsWith("robovm") && osName.startsWith("darwin")) {
    osName = "ios"
    osArch = "arm"
  } else if (osName.startsWith("mac os x")) {
    osName = "macosx"
  } else {
    val spaceIndex = osName.indexOf(' ')
    if (spaceIndex > 0) {
      osName = osName.substring(0, spaceIndex)
    }
  }
  if (osArch.equals("i386") || osArch.equals("i486") || osArch.equals("i586") || osArch
    .equals("i686")) {
    osArch = "x86"
  } else if (osArch.equals("amd64") || osArch.equals("x86-64") || osArch
    .equals("x64")) {
    osArch = "x86_64"
  } else if (osArch.startsWith("arm")) {
    osArch = "arm"
  }
  val platformName = osName + "-" + osArch
  platformName
}

lazy val gpuFlag: Boolean = {
  def process_flag(s: String) =
    if (s.toLowerCase == "true" || s == "1") true else false

  process_flag(
    Option(System.getProperty("gpu")).getOrElse("false")
  )
}

lazy val tensorflow_classifier: String = {
  val platform_splits = platform.split("-")
  val (os, arch)      = (platform_splits.head, platform_splits.last)

  val tf_c =
    if (os.contains("macosx")) "darwin-cpu-" + arch
    else if (os.contains("linux")) {
      if (gpuFlag) "linux-gpu-" + arch else "linux-cpu-" + arch
    } else ""
  tf_c
}