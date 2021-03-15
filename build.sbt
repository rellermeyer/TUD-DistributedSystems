name := "WASP"

version := "0.1"

scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "com.github.vagmcs" %% "optimus" % "3.2.4",
  "com.github.vagmcs" %% "optimus-solver-oj" % "3.2.4",
  "com.github.vagmcs" %% "optimus-solver-lp" % "3.2.4",
  "com.github.vagmcs" %% "optimus-solver-gurobi" % "3.2.4",
  "com.github.vagmcs" %% "optimus-solver-mosek" % "3.2.4"
)
