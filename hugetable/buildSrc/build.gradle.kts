plugins {
    `kotlin-dsl`
    idea
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}


/* Project configuration */
repositories {
    jcenter()
    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
}

dependencies {
    implementation("gradle.plugin.com.google.protobuf:protobuf-gradle-plugin:0.8.12")
    implementation("gradle.plugin.cz.alenkacz:gradle-scalafmt:1.13.0")
}
