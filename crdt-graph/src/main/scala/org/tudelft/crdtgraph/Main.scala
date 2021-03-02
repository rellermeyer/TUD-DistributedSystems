package org.tudelft.crdtgraph

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]) =
    CrdtgraphServer.stream[IO].compile.drain.as(ExitCode.Success)
}
