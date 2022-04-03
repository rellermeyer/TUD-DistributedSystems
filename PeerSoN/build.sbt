name := "in4391_peerson"

version := "0.1"

scalaVersion := "2.13.8"

//resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"


val AkkaVersion = "2.6.18"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-remote" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.10",
  "net.tomp2p" % "tomp2p-all" % "5.0-Beta8",
  "com.simtechdata" % "WaifUPnP" % "1.0"
)

resolvers += ("tomp2p.net" at "http://tomp2p.net/dev/mvn/").withAllowInsecureProtocol(true)