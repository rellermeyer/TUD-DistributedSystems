description = "Command line interface for HugeTable clients"

/* Build configuration */
plugins {
    `project-convention`
    application
}

application {
    mainClassName = "nl.tudelft.htable.client.cli.Main"
}

dependencies {
    implementation(project(":htable-client"))
    implementation("org.jline:jline:3.14.0")
    implementation("org.rogach:scallop_${Library.SCALA_LIB}:${Library.SCALLOP}")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.13.1")
}

