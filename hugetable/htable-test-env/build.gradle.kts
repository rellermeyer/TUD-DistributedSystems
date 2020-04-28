description = "Utility for booting embedded test environment for HugeTable without external dependencies."

/* Build configuration */
plugins {
    `project-convention`
    application
}

application {
    mainClassName = "nl.tudelft.htable.test.env.Main"
}

dependencies {
    implementation("com.typesafe.scala-logging:scala-logging_${Library.SCALA_LIB}:${Library.SCALA_LOGGING}")
    implementation("org.rogach:scallop_${Library.SCALA_LIB}:${Library.SCALLOP}")

    implementation("org.apache.curator:curator-test:${Library.CURATOR}") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j")
    }
    implementation("org.apache.hadoop:hadoop-minicluster:3.1.3") {
        exclude(group = "org.apache.curator", module = "curator-framework")
        exclude(group = "org.apache.curator", module = "curator-recipes")
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j")
    }
    runtimeOnly("org.slf4j:log4j-over-slf4j:${Library.SLF4J}")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.13.1")
    runtimeOnly("org.apache.logging.log4j:log4j-jcl:2.13.1")
}

