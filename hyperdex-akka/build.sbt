val akkaVersion = "2.6.3"
val akkaHttpVersion = "10.1.11"
val tapirVersion = "0.12.23"

enablePlugins(GatlingPlugin)

lazy val root = (project in file("."))
  .settings(
    name := "hyperdex-akka",
    version := "0.1",
    scalaVersion := "2.12.11",
    mainClass in Compile := Some("hyperdex.Main"),
    assemblyJarName in assembly := "hyperdex.jar"
  )

scalacOptions := Seq(
  "-encoding",
  "UTF-8",
  "-target:jvm-1.8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-language:higherKinds"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-json-play" % tapirVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scalatest" %% "scalatest" % "3.1.1" % "test",
  "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.3.1" % "test,it",
  "io.gatling" % "gatling-test-framework" % "3.3.1" % "test,it"
)

//Merge strategy for duplicated files for creating the fat jar. Used when running `sbt assembly`.
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) =>
    xs.map { _.toLowerCase } match {
      case "manifest.mf" :: Nil | "index.list" :: Nil | "dependencies" :: Nil =>
        MergeStrategy.discard
      case "services" :: xs =>
        MergeStrategy.filterDistinctLines
      case _ => MergeStrategy.first
    }
  case "application.conf" => MergeStrategy.concat
  case "reference.conf"   => MergeStrategy.concat
  case _                  => MergeStrategy.first
}
