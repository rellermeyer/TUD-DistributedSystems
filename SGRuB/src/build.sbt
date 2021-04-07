name := "GRuB_Scala"
organization := "org.sgrub"
version := "0.1-SNAPSHOT"
description := "GRuB for Scala"

scalaVersion := "2.12.12"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
)

libraryDependencies ++= Seq(
  "org.scorexfoundation" %% "scrypto" % "2.1.10",
)

// Ensure compatibility with Java 11
javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

val web3jVersion = "4.8.4"

// See http://docs.web3j.io/latest/advanced/web3j_core_modules/
libraryDependencies ++= Seq(
  "org.web3j"                   %  "abi"                    % web3jVersion withSources(), // Application Binary Interface encoders
  "org.web3j"                   %  "codegen"                % web3jVersion withSources(), // Code generators
  "org.web3j"                   %  "core"                   % web3jVersion withSources(), // ...Core
  "org.web3j"                   %  "geth"                   % web3jVersion withSources(), // Geth-specific JSON-RPC module
  "org.web3j"                   %  "rlp"                    % web3jVersion withSources(), // Recursive Length Prefix (RLP) encoders
  "org.web3j"                   %  "utils"                  % web3jVersion withSources(), // Minimal set of utility classes
  "org.web3j"                   %  "web3j-maven-plugin"     % "4.6.5"      withSources(), // Create Java classes from solidity contract files
)

// Logging
libraryDependencies ++= Seq(
  "ch.qos.logback"              % "logback-classic"         % "1.2.3",
  "com.typesafe.scala-logging"  %% "scala-logging"          % "3.9.2",
)

// Config
libraryDependencies += "com.typesafe" % "config" % "1.4.1"