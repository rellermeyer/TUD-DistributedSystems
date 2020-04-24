val ScalatraVersion = "2.7.0"

organization := "delft"

name := "alfredo"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.10"

resolvers += Classpaths.typesafeReleases

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % ScalatraVersion,
  "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
  "org.eclipse.jetty" % "jetty-webapp" % "9.4.19.v20190610" % "container",
  "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
  "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
  "io.spray" %%  "spray-json" % "1.3.5",
)

enablePlugins(SbtTwirl)
enablePlugins(ScalatraPlugin)

//javaOptions ++= Seq(
//  "-Xdebug",
//  "-Xrunjdwp:transport=dt_socket,address=8888,server=y,suspend=y"
//)
