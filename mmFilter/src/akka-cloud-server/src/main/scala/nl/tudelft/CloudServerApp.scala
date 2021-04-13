package nl.tudelft

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

import javax.imageio.ImageIO

import scala.util.Failure
import scala.util.Success

/**
 * The Cloud server application using akka http
 */
object CloudServerApp {

  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    // ActorSystem to start
    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 8080).bind(routes)
    futureBinding.onComplete {
          //Succesfull startup
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
          //Failed to startup
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
  def main(args: Array[String]): Unit = {


    val rootBehavior = Behaviors.setup[Nothing] { context =>

      import java.io.PrintStream
      import java.io.File
      import org.nd4j.linalg.factory.Nd4j
      import org.datavec.image.loader.NativeImageLoader

      val imageLoader = new NativeImageLoader(265, 265, 3)

      val o = new PrintStream(new File("SavedData.txt"))
      var imageCounter = 0
      // sets the sys out to the file to save the data
      System.setOut(o)

      val routes = pathPrefix("request") {
        post{
            entity(as[String]) {
              json =>
                // formats the data back to the right data
                val frames_array: Array[Double] = json.split(",").map(_.toDouble)
                val frames = Nd4j.create(frames_array).reshape(Array(1, 256)) //INDArray
                val buffer = imageLoader.asMatrixView(new File("/data/image"+imageCounter+".png"), frames) //BufferImage
                imageCounter += 1

                System.out.println("frame "+imageCounter+" "+json)
                complete("{\"status\": 200, \"message\": \"You did a POST request and your data was stored\"}") //message back to the edge server
            }
        }
      }

      startHttpServer(routes)(context.system)

      Behaviors.empty
    }

    val system = ActorSystem[Nothing](rootBehavior, "AkkaHttpCloudServer")
  }
}
