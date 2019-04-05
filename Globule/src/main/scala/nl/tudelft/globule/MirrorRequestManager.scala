package nl.tudelft.globule

import java.net.InetSocketAddress
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.model._

class MirrorRequestManager() extends Actor with ActorLogging {

  import RequestManager._

  def handleRequest(request: HttpRequest, address: InetSocketAddress): HttpResponse = {
    val suffix = if (request.uri.path.endsWithSlash) "index.html" else ""
    val path = request.uri.path.toString() + suffix
    val extension = getExtension(path)

    log.info("<- {} {}", request.method.value, path)

    val fullPath = Paths.get(Configs.DATA_DIR + path)

    log.info("-> {}", fullPath)

    // serve local files
    if (Files.exists(fullPath)) {
      // guess content type from file extension
      val mediaType = MediaTypes.forExtension(extension)
      val contentType = ContentType(mediaType, () => HttpCharsets.`UTF-8`)
      val bytes = Files.readAllBytes(fullPath)
      log.info("-> {}, Content-Type: {}", StatusCodes.OK, mediaType)
      HttpResponse(StatusCodes.OK, entity = HttpEntity(contentType, bytes))
    } else {
      // file does not exist
      log.warning("-> {}", StatusCodes.NotFound)
      HttpResponse(StatusCodes.NotFound, entity = "Unknown resource!")
    }
  }

  override def receive: Receive = {
    case HandleHttpRequest(request, address) =>
      sender() ! handleRequest(request, address)
  }

  private def getExtension(fileName: String): String = {
    val index = fileName.lastIndexOf(".")
    if (index >= 0) {
      fileName.drop(index + 1)
    } else {
      ""
    }
  }
}
