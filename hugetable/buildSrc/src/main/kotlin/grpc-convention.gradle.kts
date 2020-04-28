import com.google.protobuf.gradle.*
import org.apache.commons.lang.SystemUtils

plugins {
    `java-library`
    idea
    id("com.google.protobuf")
}

repositories {
    mavenCentral()
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${Library.PROTOBUF}"
    }

    plugins {
        val assemblySuffix = if (SystemUtils.IS_OS_WINDOWS) "bat" else "jar"
        val assemblyClassifier = if (SystemUtils.IS_OS_WINDOWS) "bat" else "assembly"

        id("scalapb") {
            artifact = "com.lightbend.akka.grpc:akka-grpc-scalapb-protoc-plugin_2.12:${Library.AKKA_GRPC}:${assemblyClassifier}@${assemblySuffix}"
        }
        id("akkaGrpc") {
            artifact = "com.lightbend.akka.grpc:akka-grpc-codegen_2.12:${Library.AKKA_GRPC}:${assemblyClassifier}@${assemblySuffix}"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.builtins {
                remove("java")
            }
            it.plugins {
                id("scalapb") {
                    option("flat_package")
                }

                id("akkaGrpc") {
                    option("language=scala")
                    // TODO Figure out a way to allow the consumer to specify these options
                    option("generate_client=true")
                    option("generate_server=true")
                    option("flat_package")
                }
            }
        }
    }
}

sourceSets {
    main {
        withConvention(ScalaSourceSet::class) {
            scala.srcDirs(
                "${protobuf.protobuf.generatedFilesBaseDir}/main/scalapb",
                "${protobuf.protobuf.generatedFilesBaseDir}/main/akkaGrpc"
            )
        }
    }
}

idea {
    module {
        generatedSourceDirs.add(file("${protobuf.protobuf.generatedFilesBaseDir}/main/scalapb"))
        generatedSourceDirs.add(file("${protobuf.protobuf.generatedFilesBaseDir}/main/akkaGrpc"))
    }
}

dependencies {
    api("com.lightbend.akka.grpc:akka-grpc-runtime_${Library.SCALA_LIB}:${Library.AKKA_GRPC}")

    // Force Akka 2.6.4
    api("com.typesafe.akka:akka-stream_${Library.SCALA_LIB}:${Library.AKKA}")
    api("com.typesafe.akka:akka-discovery_${Library.SCALA_LIB}:${Library.AKKA}")

    implementation("io.grpc:grpc-protobuf:${Library.GRPC}")
    implementation("io.grpc:grpc-netty-shaded:${Library.GRPC}")
}
