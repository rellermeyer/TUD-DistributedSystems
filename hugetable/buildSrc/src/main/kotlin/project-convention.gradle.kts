plugins {
    `java-library`
    scala
    id("cz.alenkacz.gradle.scalafmt")
}

/* Project configuration */
repositories {
    mavenCentral()
    jcenter()
}

tasks.test {
    useJUnitPlatform {
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    reports.html.isEnabled = true
}


dependencies {
    implementation("org.scala-lang:scala-library:${Library.SCALA}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${Library.JUNIT_JUPITER}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${Library.JUNIT_JUPITER}")
}

