description = "Module for running HugeTable servers"

/* Build configuration */
plugins {
    `project-convention`
}

dependencies {
    api(project(":htable-storage"))
    api("com.typesafe.akka:akka-actor-typed_${Library.SCALA_LIB}:${Library.AKKA}")
    api("com.typesafe.akka:akka-stream-typed_${Library.SCALA_LIB}:${Library.AKKA}")
    api("org.apache.curator:curator-framework:${Library.CURATOR}") {
        exclude(group = "log4j")
    }

    implementation(project(":htable-protocol"))
    implementation(project(":htable-client"))
    implementation("org.apache.curator:curator-recipes:${Library.CURATOR}") {
        exclude(group = "log4j")
    }
}

