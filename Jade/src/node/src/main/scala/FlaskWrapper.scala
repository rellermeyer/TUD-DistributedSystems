import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

import com.spotify.docker.client.messages.{ContainerConfig, ProgressMessage}

/**
  * A wrapper implementation for a Flask Docker container.
  *
  * @param wrapperConfiguration the wrapper configuration
  */
final class FlaskWrapper(wrapperConfiguration: WrapperConfiguration) extends DockerWrapper(wrapperConfiguration) {
    override def instantiateDockerContainer(): String = {
        // Build/configure the container from the image and return its id
        val containerConfig = configurePorts(ContainerConfig.builder)
            .image(buildDockerImage("flask"))
            .build
        val creation = docker.createContainer(containerConfig)
        creation.id
    }
}
