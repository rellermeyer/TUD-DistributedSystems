import sbt.Keys.libraryDependencies
enablePlugins(DockerPlugin)

enablePlugins(JavaAppPackaging)
enablePlugins(AshScriptPlugin)

name := "CRUSH"
version := "0.1"
scalaVersion := "2.13.5"
version := "0.1"

val AkkaVersion = "2.6.13"
dockerBaseImage := "adoptopenjdk/openjdk11:x86_64-debianslim-jre-11.0.10_9"
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.5" % "test",
  "org.scalactic" %% "scalactic" % "3.2.5",
  "com.typesafe" % "config" % "1.4.1",
  "io.methvin" %% "directory-watcher-better-files" % "0.15.0",
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-remote" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-remote" % AkkaVersion,
  "org.json4s" %% "json4s-native" % "3.7.0-M14",
  "org.apache.commons" % "commons-math3" % "3.6.1"
)


assemblyMergeStrategy in assembly := {
  case PathList("jackson-annotations-2.10.5.jar", xs @ _*) => MergeStrategy.last
  case PathList("jackson-core-2.10.5.jar", xs @ _*) => MergeStrategy.last
  case PathList("jackson-databind-2.10.5.jar", xs @ _*) => MergeStrategy.last
  case PathList("jackson-dataformat-cbor-2.10.5.jar", xs @ _*) => MergeStrategy.last
  case PathList("jackson-datatype-jdk8-2.10.5.jar", xs @ _*) => MergeStrategy.last
  case PathList("jackson-datatype-jsr310-2.10.5.jar", xs @ _*) => MergeStrategy.last
  case PathList("jackson-module-parameter-names-2.10.5.jar", xs @ _*) => MergeStrategy.last
  case PathList("jackson-module-paranamer-2.10.5.jar", xs @ _*) => MergeStrategy.last
  case _ => MergeStrategy.first
}


libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"