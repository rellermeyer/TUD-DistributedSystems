name := "akka-scala-MIDD"

version := "1.0"

Test / scalaSource := baseDirectory.value / "experiments" / "scala"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.6.18"

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

// By default, the standard input of the sbt process is not forwarded to the forked process.
// To connect input from sbt to AkkaMain, this is enabled:
run / connectInput := true

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.10",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.9" % Test
)
