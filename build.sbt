// *****************************************************************************
// Main Info
// *****************************************************************************

lazy val root =
  project
    .in(file("."))
    .settings(commonSettings)
    .settings(
      crossPaths := false,
      autoScalaLibrary := false,
      mainClass in Compile := (mainClass in Compile in tensorFlow).value,
      assemblySettings
    )
    .aggregate(tensorFlow)

//lazy val dl4j =
//  project
//    .in(file("dl4j"))
//    .settings(settings)
//    .settings(
//      name := "dl4j",
//      scalaVersion := "2.11.12", // ScalNet and ND4S are only available for Scala 2.11
//      libraryDependencies ++= Seq(
//        library.dl4j,
//        library.dl4jCuda,
//        library.dl4jUi,
//        library.logbackClassic,
//        library.nd4jNativePlatform,
//        library.scalNet
//      )
//    )

lazy val tensorFlow =
  project
    .in(file("tensorflow"))
    .settings(commonSettings)
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
        library.akkaScala,
        library.confScala
      ),
      mainClass in (Compile, run) := Some("ru.able.Main"),
      mainClass in assembly := Some("ru.able.Main"),
      resourceDirectory in Compile := file(".") / "./tensorflow/src/main/resources",
      resourceDirectory in Runtime := file(".") / "./tensorflow/src/main/resources",
      fork := true, // prevent classloader issues caused by sbt and opencv
      assemblySettings
    )

lazy val AblePlatform =
  project
    .in(file("able-platform"))
    .settings(commonSettings)
    .settings(
      name := "able-platform",
      PB.targets in Compile := Seq(
        scalapb.gen() -> (sourceManaged in Compile).value
      ),
      javaCppPresetLibs ++= Seq(
        "ffmpeg" -> "3.4.1"
      ),
      libraryDependencies ++= Seq(
        library.loggingScala,
        library.logbackClassic,
        library.janino,
        library.tensorFlow,
        library.tensorFlowData,
        library.akkaScala,
        library.protobufScala,
        library.akkaVisual
      ),
      mainClass in (Compile, run) := Some("ru.able.AblePlatform"),
      mainClass in assembly := Some("ru.able.AblePlatform"),
      resourceDirectory in Compile := file(".") / "./able-platform/src/main/resources",
      resourceDirectory in Runtime := file(".") / "./able-platform/src/main/resources",
      fork := true, // prevent classloader issues caused by sbt and opencv
      assemblySettings
    )

lazy val AbleClient =
  project
    .in(file("able-client"))
    .settings(commonSettings)
    .settings(
      name := "able-client",
      javaCppPresetLibs ++= Seq(
        "ffmpeg" -> "3.4.1"
      ),
      libraryDependencies ++= Seq(
        library.loggingScala,
        library.logbackClassic,
        library.akkaScala,
        library.janino,
        library.googleInject,
        library.akkaTest,
        library.scalaTest,
        library.mockitoScala,
        library.akkaStreamTest
      ),
      mainClass in (Compile, run) := Some("ru.able.AbleClient"),
      mainClass in assembly := Some("ru.able.AbleClient"),
      resourceDirectory in Compile := file(".") / "./able-client/src/main/resources",
      resourceDirectory in Runtime := file(".") / "./able-client/src/main/resources",
      fork := true, // prevent classloader issues caused by sbt and opencv
      assemblySettings
    )


lazy val SharedLibrary =
  project
    .in(file("shared-library"))
    .settings(lazySettings)
    .settings(
      javaCppPresetLibs ++= Seq(
        "ffmpeg" -> "3.4.1"
      ),
      libraryDependencies ++= Seq(
        library.akkaScala,
        library.javacvScala
      )
    )

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
      val akkaTest =        "2.6.10"
      val akkaScala =       "2.6.10"
      val tensorFlow =      "0.2.4"
      val protobufScala =   "0.7.4"
      val confScala =       "1.4.0"
      val loggingScala =    "3.9.0"
      val javacvScala =     "1.4"
      val guiceScala =      "4.1.0"
      val mockitoScala =    "1.10.19"
      val akkaVisual =      "1.1.0"
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
    val akkaTest =            "com.typesafe.akka"     %% "akka-testkit"           % Version.akkaTest
    val akkaStreamTest =      "com.typesafe.akka"     %% "akka-stream-testkit"    % Version.akkaTest
    val scalNet =             "org.deeplearning4j"    %% "scalnet"                % Version.dl4j
    val tensorFlow =          "org.platanios"         %% "tensorflow"             % Version.tensorFlow classifier tensorflow_classifier
    val tensorFlowData =      "org.platanios"         %% "tensorflow-data"        % Version.tensorFlow
    val protobufScala =       "com.thesamet.scalapb"  %% "scalapb-runtime"        % Version.protobufScala % "protobuf"
    val akkaScala =           "com.typesafe.akka"     %% "akka-stream"            % Version.akkaScala
    val confScala =           "com.typesafe"          % "config"                  % Version.confScala
    val loggingScala =        "com.typesafe.scala-logging" %% "scala-logging"     % Version.loggingScala
    val javacvScala =         "org.bytedeco"          % "javacv-platform"         % Version.javacvScala
    val googleInject =        "com.google.inject"     % "guice"                   % Version.guiceScala
    val mockitoScala =        "org.mockito"           % "mockito-all"             % Version.mockitoScala
    val akkaVisual =          "de.aktey.akka.visualmailbox" %% "collector"        % Version.akkaVisual
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
        case _ => MergeStrategy.first
      }
    case _ => MergeStrategy.first
  }
)

// *****************************************************************************
// Common settings
// *****************************************************************************

lazy val lazySettings = Seq(
  organization := "ru.able",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.12.6"
)

lazy val commonSettings = Seq(
  name := "able-ai-platform",
  organization := "ru.able",
  organizationName := "Able AI",
  developers := List(Developer("allowq", "Nikita Kupriyanov", "", url("https://github.com/allowq"))),
  licenses += ("GNU Lesser GPL 3.0", url("https://www.gnu.org/licenses/lgpl-3.0.html")),
  startYear := Some(2020),

  logLevel := Level.Debug,
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