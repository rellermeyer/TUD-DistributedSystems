name := "Fruitarian"

version := "1.0"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.6.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.json4s" %% "json4s-native" % "3.7.0-M2",
  "org.json4s" %% "json4s-jackson" % "3.7.0-M2",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test
)

enablePlugins(JavaAppPackaging)
mainClass in Compile := Some("nl.tudelft.fruitarian.Main")