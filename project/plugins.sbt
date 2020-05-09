addSbtPlugin("de.heikoseeberger" % "sbt-header"       % "4.0.0")
addSbtPlugin("io.get-coursier"   % "sbt-coursier"     % "1.0.1")
addSbtPlugin("org.bytedeco"      % "sbt-javacv"       % "1.16")
addSbtPlugin("com.thesamet"      % "sbt-protoc"       % "0.99.18")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"     % "0.14.5")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.7.4"