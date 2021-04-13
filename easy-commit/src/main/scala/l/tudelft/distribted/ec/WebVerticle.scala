package l.tudelft.distribted.ec

import io.vertx.core.json.Json
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.client.WebClient
import io.vertx.scala.ext.web.handler.BodyHandler
import l.tudelft.distribted.ec.protocols.NetworkingHandler

import scala.concurrent.Future

class WebVerticle() extends ScalaVerticle {
  override def startFuture(): Future[_] = {
    val network = new NetworkingHandler(vertx, "web", true)
    network.listen()

    val router = Router.router(vertx)

    router.get("/api/network").handler(request => {
      request.response().end(Json.encodeToBuffer(network.network))
    })

    val webClient = WebClient.create(vertx)


    router
      .get("/api/retrieve/:port/:key")
      .handler(ctx => {
        val port = ctx.pathParam("port").get.toInt
        webClient.get(port, "0.0.0.0", "/retrieve/" + ctx.pathParam("key").get).send(response => {
          response.result().body() match {
            case None => ctx.response().end()
            case Some(data) => ctx.response().end(data)
          }

        })
      })

    router
      .get("/api/remove/:port/:key")
      .handler(ctx => {
        val port = ctx.pathParam("port").get.toInt
        webClient.get(port, "0.0.0.0", "/remove/" + ctx.pathParam("key").get).send(response => {
          response.result().body() match {
            case None => ctx.response().end()
            case Some(data) => ctx.response().end(data)
          }

        })
      })
    router
      .post("/api/store/:port/:key")
      .handler(BodyHandler.create())
      .handler(ctx => {
        val port = ctx.pathParam("port").get.toInt
        webClient.post(port, "0.0.0.0", "/store/" + ctx.pathParam("key").get).sendJson(ctx.getBody().get, response => {
          response.result().body() match {
            case None => ctx.response().end()
            case Some(data) => ctx.response().end(data)
          }
        })
      })


    vertx
      .createHttpServer()
      .requestHandler(router)
      .listenFuture(5000, "0.0.0.0")
  }
}

