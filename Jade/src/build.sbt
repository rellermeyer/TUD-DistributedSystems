import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerExposedPorts
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._

lazy val commonSettings = Seq(
    version := "0.1",
    scalaVersion := "2.12.8",
    // Dependencies
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.5.21",
        "com.typesafe.akka" %% "akka-remote" % "2.5.21",
        "com.spotify" % "docker-client" % "8.15.1",
        "org.slf4j" % "slf4j-simple" % "1.7.5",
        "com.typesafe.akka" %% "akka-testkit" % "2.5.21" % Test,
        "org.scalatest" %% "scalatest" % "3.0.5" % Test,
        "io.circe" %% "circe-core" % "0.10.0",
        "io.circe" %% "circe-generic" % "0.10.0",
        "io.circe" %% "circe-parser" % "0.10.0",
    ),

    // Do not move to project/plugins.sbt, due to bug
    resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases",
    addSbtPlugin("com.artima.supersafe" % "sbtplugin" % "1.1.3"),
)

// The common library
lazy val common = Project("common", file("common"))
    .settings(commonSettings)

// The local project
lazy val local = Project("manager", file("manager"))
    .settings(
        commonSettings,
        mainClass in Compile := Some("JadeLauncher"),
        dockerExposedPorts := Seq(2551)
    )
    .enablePlugins(JavaAppPackaging, DockerPlugin)
    .dependsOn(common)

// The remote project
lazy val remote = Project("node", file("node"))
    .settings(
        commonSettings,
        mainClass in Compile := Some("NodeLauncher"),
        dockerExposedPorts := Seq(2552),
        mappings.in(Universal) ++= contentOf(sourceDirectory.value / "main" / "images"),
        dockerCommands += Cmd("USER", "root"),
        dockerCommands += Cmd("RUN", "sed -i.bak '/https/d' /etc/apt/sources.list"),
        dockerCommands += Cmd("RUN", "apt-get update && apt-get install -y apt-transport-https"),
        dockerCommands += Cmd("RUN", "cp -f /etc/apt/sources.list.bak /etc/apt/sources.list"),
        dockerCommands += Cmd("RUN", "rm /etc/apt/sources.list.bak"),
        dockerCommands += Cmd("RUN", "apt-get update"),
        dockerCommands += Cmd("RUN", "apt-get install -y ca-certificates curl lxc iptables"),
        dockerCommands += Cmd("RUN", "curl -sSL https://get.docker.com/ | sh"),
        dockerCommands += Cmd("USER", "1001")
    )
    .enablePlugins(JavaAppPackaging, DockerPlugin)
    .dependsOn(common)

// Options
scalastyleFailOnWarning := true
