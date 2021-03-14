name := "WASP"

version := "0.1"

scalaVersion := "2.13.5"

enablePlugins(ProtobufPlugin)

// https://mvnrepository.com/artifact/io.github.oliviercailloux/google-or-tools
libraryDependencies += "io.github.oliviercailloux" % "google-or-tools" % "6.7.2"

// https://mvnrepository.com/artifact/com.panavis.open-source/ortools-linux-x86-64
libraryDependencies += "com.panavis.open-source" % "ortools-linux-x86-64" % "7.8.7959"
