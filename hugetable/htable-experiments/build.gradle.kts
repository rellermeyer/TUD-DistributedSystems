description = "Experiments for HugeTable implementation"

/* Build configuration */
plugins {
    `project-convention`
    application
}

dependencies {
    implementation(project(":htable-client"))
    implementation("org.rogach:scallop_${Library.SCALA_LIB}:${Library.SCALLOP}")
    implementation("com.typesafe.scala-logging:scala-logging_${Library.SCALA_LIB}:${Library.SCALA_LOGGING}")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.13.1")
}

// Create binaries for the experiments
application {
    mainClassName = "nl.tudelft.htable.experiments.ExperimentRunner"
}

tasks.named<CreateStartScripts>("startScripts") {
    applicationName = "experiment-runner"
}
