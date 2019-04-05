package nl.tudelft.globule

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, ActorSelection, Props}
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.util.Timeout
import nl.tudelft.globule.ReplicationManager.DocumentRequest

import scala.concurrent.Await
import scala.concurrent.duration._

object RequestManager {

  // from WebServer
  case class HandleHttpRequest(request: HttpRequest, address: InetSocketAddress)

  // from ReplicationManager
  case class ServeDocument(fileServer: FileServer, fileRequest: FileRequest)

  def props(replicationManager: ActorSelection, servername: String) = Props(new RequestManager(replicationManager, servername))
}

class RequestManager(replicationManager: ActorSelection, servername: String) extends Actor with ActorLogging {

  import Location._
  import RequestManager._

  def handleRequest(request: HttpRequest, address: InetSocketAddress): HttpResponse = {
    val suffix = if (request.uri.path.endsWithSlash) "index.html" else ""
    val path = request.uri.path.toString() + suffix
    val extension = getExtension(path)

    log.info("<- {} {}", request.method.value, path)

    val fullPath = Paths.get(Configs.DATA_DIR + "/" + Configs.app.getString("servername") + path)

    // we do not redirect html files
    val isHtml = extension == "html"

    val ip = address.getAddress.getHostAddress
    //val ip = "81.169.181.179" // IP from Germany
    val location = lookupIpLocation(ip)


    if (!isHtml) {

      log.info("IP lookup: " + ip + " -> " + location)

      val url = getRedirectionUrl(path, ip, location)
      if (url.isDefined) {
        return HttpResponse(StatusCodes.TemporaryRedirect, headers.Location(Uri(url.get)) :: Nil)
      }
    }

    log.info("-> {}", fullPath)

    // serve local files
    if (Files.exists(fullPath)) {
      // guess content type from file extension
      val mediaType = MediaTypes.forExtension(extension)
      val contentType = ContentType(mediaType, () => HttpCharsets.`UTF-8`)
      val bytes = Files.readAllBytes(fullPath)

      if (isHtml) {
        val documentHtmlString = new String(bytes, StandardCharsets.UTF_8)

        val relativeURLs = HTMLDocumentSubstitution.extractRelativeURLs(documentHtmlString)

        val optionURLs = relativeURLs.map(path => {
          path -> getLocalOrRedirectUrl(path, ip, location)
        }).toMap

        val modifiedHTMLString = HTMLDocumentSubstitution.substituteRelativeToNew(optionURLs, documentHtmlString)

        log.info("-> {}, Content-Type: {}", StatusCodes.OK, mediaType)
        return HttpResponse(StatusCodes.OK, entity = HttpEntity(contentType, modifiedHTMLString.getBytes))
      } else {
        log.info("-> {}, Content-Type: {}", StatusCodes.OK, mediaType)
        return HttpResponse(StatusCodes.OK, entity = HttpEntity(contentType, bytes))
      }

    } else {
      // file does not exist
      log.warning("-> {}", StatusCodes.NotFound)
      HttpResponse(StatusCodes.NotFound, entity = "Unknown resource!")
    }
  }

  private def getLocalOrRedirectUrl(path: String, ip: String, location: Option[Location]): String = {
    val redirOption = getRedirectionUrl(path, ip, location)
    if (redirOption.isDefined) {
      redirOption.get
    } else {
      path
    }
  }

  // check with replication manager if we should redirect
  private def getRedirectionUrl(path: String, ip: String, location: Option[Location]): Option[String] = {
    if (location.isDefined) {
      val pathParts = path.split("/")
      val server = pathParts.head
      val filepath = pathParts.filter(el => {
        el.nonEmpty
      }).mkString("/")
      val file = new FileDescription(servername, filepath)
      val fileRequest = new FileRequest(file, ip, location.get)
      implicit val timeout: Timeout = Timeout(Configs.app.getInt("webserver-timeout").seconds)
      val future = replicationManager ? DocumentRequest(fileRequest)
      val serveDocument = Await.result(future, Configs.app.getInt("webserver-timeout").seconds).asInstanceOf[ServeDocument]
      // TODO: handle timeout

      // if master, serve file from self
      if (!serveDocument.fileServer.master) {
        Some("http://" + serveDocument.fileServer.mirrorServerInfo.hostname + ":" +
          serveDocument.fileServer.mirrorServerInfo.port + "/" +
          serveDocument.fileRequest.file.servername + "/" + path)
      } else {
        None
      }
    } else {
      None
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
