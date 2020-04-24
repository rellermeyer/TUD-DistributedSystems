package com.github.arucard21.globe.replicator.lookupservice

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object LSApplication extends App {
  LSServer.startServer("0.0.0.0", 8080)
}
