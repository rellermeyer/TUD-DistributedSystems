description = "Storage driver for HugeTable using HBase"

/* Build configuration */
plugins {
    `project-convention`
}

dependencies {
    api(project(":htable-storage"))
    implementation("com.typesafe.scala-logging:scala-logging_${Library.SCALA_LIB}:${Library.SCALA_LOGGING}")


    implementation("org.apache.hbase:hbase-server:2.2.4") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j")
    }
    implementation("org.apache.hadoop:hadoop-client:3.1.3") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j")
    }
    runtimeOnly("org.slf4j:log4j-over-slf4j:${Library.SLF4J}")
}
