description = "Command line interface for HugeTable servers"

/* Build configuration */
plugins {
    `project-convention`
    application
}

application {
    mainClassName = "nl.tudelft.htable.server.cli.Main"
}

dependencies {
    implementation(project(":htable-server"))
    implementation("org.rogach:scallop_${Library.SCALA_LIB}:${Library.SCALLOP}")

    implementation("org.apache.hadoop:hadoop-client:3.0.3") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j")
    }
    implementation(project(":htable-storage-hbase"))
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.13.1")
    runtimeOnly("org.apache.logging.log4j:log4j-jcl:2.13.1")
}

