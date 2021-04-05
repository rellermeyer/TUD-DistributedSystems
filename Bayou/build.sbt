name := "client"

organization := "in4391"

version := "0.1"

scalaVersion := "2.12.8"

mainClass := Some("ClientStart")

enablePlugins(DockerPlugin, JavaAppPackaging)

dockerEntrypoint := Seq("/opt/docker/bin/client-start")

dockerExposedPorts := Seq(9990, 10000)