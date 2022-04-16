val scala2Version = "2.13.8"

import sbt.Package.ManifestAttributes

lazy val root = project
  .in(file("."))
  .settings(
    name := "revc-implementation",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala2Version,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % "2.6.18",
      "ch.qos.logback" % "logback-classic" % "1.2.10" % Runtime,
      "com.novocode" % "junit-interface" % "0.11" % "test"
    ),
    packageOptions := Seq(ManifestAttributes(
      ("Premain-class", "ObjectSizeFetcher")
    ))
  )
