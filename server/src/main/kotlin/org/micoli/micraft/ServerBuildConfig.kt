package org.micoli.micraft

val SERVER_BUILD_TIMESTAMP: String =
    object {}
        .javaClass
        .getResourceAsStream("/server-build-timestamp.txt")
        ?.bufferedReader()
        ?.readText()
        ?.trim() ?: "unknown"
