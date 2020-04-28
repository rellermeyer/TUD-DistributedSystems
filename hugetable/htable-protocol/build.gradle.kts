
description = "Protobuf definitions used by HTable server endpoints"

/* Build configuration */
plugins {
    scala // XXX Needs to be up top: https://github.com/gradle/gradle/issues/12611
    `grpc-convention`
}

dependencies {
    api(project(":htable-core"))
}
