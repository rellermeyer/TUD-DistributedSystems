import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

import com.spotify.docker.client.messages.{ContainerConfig, ProgressMessage}

/**
  * A wrapper implementation for a Postgres Docker container.
  *
  * @param wrapperConfiguration the wrapper configuration
  */
final class PostgresWrapper(wrapperConfiguration: WrapperConfiguration) extends DockerWrapper(wrapperConfiguration) {
    def instantiateDockerContainer(): String = {
        // Build/configure the container from the image and return its id
        val containerConfig = configurePorts(ContainerConfig.builder)
            .image(buildDockerImage("postgres"))
            .build
        val creation = docker.createContainer(containerConfig)
        creation.id
    }
}
