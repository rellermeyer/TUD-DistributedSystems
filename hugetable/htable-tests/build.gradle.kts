description = "Test suite for HugeTable."

/* Build configuration */
plugins {
    `project-convention`
}

dependencies {
    testImplementation(project(":htable-server"))
    testImplementation(project(":htable-client"))
    testImplementation(project(":htable-storage-hbase"))
    testImplementation("org.apache.curator:curator-test:${Library.CURATOR}") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j")
    }
    testImplementation("org.apache.hadoop:hadoop-minicluster:3.1.3") {
        exclude(group = "org.apache.curator", module = "curator-framework")
        exclude(group = "org.apache.curator", module = "curator-recipes")
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j")
    }
    testImplementation("com.typesafe.akka:akka-stream-testkit_${Library.SCALA_LIB}:${Library.AKKA}")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.13.1")
    testRuntimeOnly("org.apache.logging.log4j:log4j-jcl:2.13.1")
}
