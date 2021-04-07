
name := "mmfilter"

version := "0.1"

scalaVersion := "2.12.13"

enablePlugins(sbtdocker.DockerPlugin, JavaAppPackaging)

docker / dockerfile := {
  val appDir = stage.value
  val targetDir = "/app"

  new Dockerfile {
    from("openjdk:8-jre")
    entryPoint(s"$targetDir/bin/${executableScriptName.value}")
    copy(appDir, targetDir)
  }
}

docker / buildOptions := BuildOptions(cache = false)

// dl4j
libraryDependencies += "org.deeplearning4j" % "deeplearning4j-core" % "1.0.0-beta7"
libraryDependencies += "org.nd4j" % "nd4j-native-platform" % "1.0.0-beta7"

// slf4j
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3" % Runtime

fork in run := true

