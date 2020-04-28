description = "Module for running the HugeTable storage layer"

/* Build configuration */
plugins {
    `project-convention`
}

dependencies {
    api(project(":htable-core"))
    api("com.typesafe.akka:akka-stream_${Library.SCALA_LIB}:${Library.AKKA}")
}
