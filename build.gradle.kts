plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

/**
 * ./gradlew dev
 *
 * Builds server + client, then starts both in parallel:
 *   - Ktor game server  on :8080
 *   - Webpack dev server on :8081 (hot-reload)
 *
 * Ctrl+C stops both processes.
 */
tasks.register("dev") {
    group = "micraft"
    description = "Build and start the game server (:8080) and the webpack dev server (:8081)"

    dependsOn(":server:installDist", ":app:webApp:wasmJsBrowserDevelopmentWebpack")

    doLast {
        val gradle = "${rootProject.projectDir}/gradlew"

        val serverProc = ProcessBuilder(gradle, ":server:run")
            .directory(rootProject.projectDir)
            .inheritIO()
            .start()

        val clientProc = ProcessBuilder(gradle, ":app:webApp:wasmJsBrowserDevelopmentRun",
            "--continuous")
            .directory(rootProject.projectDir)
            .inheritIO()
            .start()

        Runtime.getRuntime().addShutdownHook(Thread {
            serverProc.destroyForcibly()
            clientProc.destroyForcibly()
        })

        // Block until either process exits (normally Ctrl+C triggers the shutdown hook)
        try {
            clientProc.waitFor()
        } finally {
            serverProc.destroyForcibly()
        }
    }
}