description = "Client library for communicating with HugeTable servers"

/* Build configuration */
plugins {
    `project-convention`
}

dependencies {
    api(project(":htable-core"))
    api("org.apache.curator:curator-framework:${Library.CURATOR}") {
        exclude(group = "log4j")
    }
    api("com.typesafe.akka:akka-stream_${Library.SCALA_LIB}:${Library.AKKA}")

    implementation(project(":htable-protocol"))
    implementation("io.grpc:grpc-stub:1.28.0")
}
