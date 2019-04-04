import java.io.File
import java.nio.file.Paths
import java.util
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

import ComponentState.ComponentState
import com.spotify.docker.client.DockerClient.ExecCreateParam
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig, PortBinding, ProgressMessage}
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/**
  * A specialized wrapper for Docker containers. Starts the container upon instantiation.
  *
  * @param wrapperConfiguration the wrapper configuration
  */
abstract class DockerWrapper(wrapperConfiguration: WrapperConfiguration) extends Wrapper(wrapperConfiguration) {
    final protected val docker: DefaultDockerClient = DefaultDockerClient.fromEnv.build
    final protected val id: String = instantiateDockerContainer()
    docker.startContainer(id)
    println("Started docker wrapper")

    final override def stop(): Unit = {
        try {
            docker.killContainer(id)
            docker.removeContainer(id)
            docker.close()
        } catch {
            case _: Throwable => println("Wrapped application is already killed/stopped")
        }
    }

    /**
      * Performs the tasks necessary to instantiate the Docker container.
      *
      * @return the id of the container
      */
    def instantiateDockerContainer(): String

    override def heartbeat(): Future[ComponentState] = Future {
        // Future in Future. The outer Future checks for time-out, the inner one performs the operation
        try {
            Await.ready(Future {
                clientAlive() && containerAlive()
            }, ConnectionConfig.timeout.duration).value.get match {
                case Success(success) => if (success) ComponentState.Running else ComponentState.Failed
                case Failure(t) =>
                    println(s"Wrapper heartbeat failed $t")
                    ComponentState.Failed
            }
        } catch {
            case _: TimeoutException =>
                println("Docker wrapper heartbeat failed: Timeout Exception")
                ComponentState.Failed
        }
    }

    /**
      * Checks whether the docker client is alive.
      *
      * @return true if there is an "OK" response, false otherwise.
      */
    final protected def clientAlive(): Boolean = {
        val ping = docker.ping
        println(s"Docker wrapper ping: $ping")
        ping == "OK"
    }

    /**
      * Checks whether the docker container is alive by executing an echo command on it.
      *
      * @return true if there is an "hello" response, false otherwise.
      */
    protected def containerAlive(): Boolean = {
        val command = Array("sh", "-c", "echo hello")
        val execCreation = docker.execCreate(id, command, DockerClient.ExecCreateParam.attachStdout,
            DockerClient.ExecCreateParam.attachStderr, ExecCreateParam.attachStdin)
        val output = docker.execStart(execCreation.id)
        val alive = output.readFully.trim
        println(s"Docker wrapper alive: $alive")
        alive == "hello"
    }

    /**
      * Exposes the ports specified in the wrapper configuration for both the host and the container.
      *
      * @param builder the container config builder
      * @return the container config builder with hostConfig and exposedPorts set
      */
    //TODO: Need something here (or in the Flask wrapper) that sets environment vars to the target ports
    protected def configurePorts(builder: ContainerConfig.Builder): ContainerConfig.Builder = {
        val portBindings = new java.util.HashMap[String, java.util.List[PortBinding]]()
        for (port <- config.localPorts) {
            val hostPorts = new java.util.ArrayList[PortBinding]
            hostPorts.add(PortBinding.of(WrapperConfiguration.defaultIP, port))
            portBindings.put(port, hostPorts)
        }
        val hostConfig = HostConfig.builder
            .portBindings(portBindings)
            .build
        val containerConfig = builder
            .hostConfig(hostConfig)
            .exposedPorts(config.localPorts: _*)
        config.targetAddress match {
            case Some(address) => containerConfig.env(s"targetIp=${address.ip}" ::
                    s"targetPort=${address.port}"
                    :: Nil : _*)
            case None => containerConfig
        }
    }

    /**
      * Builds the docker image in the given resource folder.
      *
      * @param folderName name of the resource folder with the Dockerfile + contents
      * @return a docker image id
      */
    protected def buildDockerImage(folderName: String): String = {
        // Obtain path to dockerfile in resources
        val dockerPath = Paths.get(s"/opt/docker/$folderName")
        // The recommended way to build a Dockerfile
        val imageIdFromMessage = new AtomicReference[String]
        println(dockerPath.toAbsolutePath)
        docker.ping()
        docker.build(dockerPath.toAbsolutePath, s"jade-$folderName", (message: ProgressMessage) => {
            val imageId = message.buildImageId
            if (imageId != null) imageIdFromMessage.set(imageId)
        })
    }
}
