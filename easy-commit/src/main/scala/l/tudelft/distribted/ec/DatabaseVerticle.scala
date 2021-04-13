package l.tudelft.distribted.ec

import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.Router
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.vertx.core.json.Json
import io.vertx.ext.web.handler.sockjs.impl.JsonCodec
import io.vertx.lang.scala.json.JsonObject
import io.vertx.scala.ext.web.handler.BodyHandler
import l.tudelft.distribted.ec.protocols.{EasyCommitProtocol, RemoveDataTransaction, RequestNetwork, StoreDataTransaction}

import java.util.UUID
import scala.concurrent.Future

class DatabaseVerticle(val name: String, val port: Int) extends ScalaVerticle {

  override def startFuture(): Future[_] = {
    val router = Router.router(vertx)


    val database = new HashMapDatabase()
    Json.mapper.registerModule(DefaultScalaModule)

    val protocol = new EasyCommitProtocol(
      vertx,
      name,
      database
    )

    router
      .post("/store/:key")
      .handler(BodyHandler.create())
      .handler(ctx => {
        val key = ctx.pathParam("key").get
        val data = ctx.getBody().get.toJsonObject
        protocol.requestTransaction(StoreDataTransaction(UUID.randomUUID().toString, key, data.getMap))
        ctx.response().end("OK")
      }).failureHandler(x =>println(x.failure()))

    router
      .get("/retrieve/:key")
      .handler(ctx => {
        val key = ctx.pathParam("key").get
        println(key)
        database.retrieve(key) match {
          case Some(data) =>
            val jsonObject = new JsonObject(data)
            ctx.response().end(jsonObject.toBuffer)
          case _ =>
            ctx.response().setStatusCode(404).end()
        }
      })

    router
      .get("/remove/:key")
      .handler(ctx => {
        val key = ctx.pathParam("key").get
        protocol.requestTransaction(new RemoveDataTransaction(UUID.randomUUID().toString, key))
        ctx.response().end("OK")
      })

    protocol.listen()

    vertx
      .createHttpServer()
      .requestHandler(router.accept _)
      .listenFuture(port, "0.0.0.0")
  }
}

object Main {
  def main (args: Array[String] ): Unit = {
    val vertx = Vertx.vertx
    vertx.deployVerticle(new DatabaseVerticle("8888", 8888))
    vertx.deployVerticle(new DatabaseVerticle("7777", 7777))
    vertx.deployVerticle(new DatabaseVerticle("9999", 9999))
    vertx.deployVerticle(new WebVerticle())
  }
}
