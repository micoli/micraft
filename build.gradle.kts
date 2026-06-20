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
 * Touching run.lock restarts the server without stopping the client.
 * Ctrl+C stops both processes.
 */
tasks.register("dev") {
    group = "micraft"
    description = "Build and start the game server (:8080) and the webpack dev server (:8081)"

    dependsOn(":server:installDist", ":app:webApp:wasmJsBrowserDevelopmentWebpack")

    doLast {
        val gradle   = "${rootProject.projectDir}/gradlew"
        val lockFile = rootProject.projectDir.resolve("run.lock")
        val serverBin = rootProject.file("server/build/install/server/bin/server")

        // Kill a process AND all its JVM children (avoids gradle-wrapper orphan problem)
        fun killTree(p: Process) {
            p.descendants().forEach { it.destroyForcibly() }
            p.destroyForcibly()
            p.waitFor()
        }

        fun startServer(): Process {
            println("[dev] starting server (${java.time.LocalTime.now().let { "%02d:%02d:%02d".format(it.hour, it.minute, it.second) }})")
            return ProcessBuilder(serverBin.absolutePath)
                .directory(rootProject.projectDir)
                .inheritIO()
                .start()
        }

        val serverRef  = java.util.concurrent.atomic.AtomicReference(startServer())

        val clientProc = ProcessBuilder(gradle, ":app:webApp:wasmJsBrowserDevelopmentRun",
            "--continuous")
            .directory(rootProject.projectDir)
            .inheritIO()
            .start()

        Runtime.getRuntime().addShutdownHook(Thread {
            killTree(serverRef.get())
            killTree(clientProc)
        })

        // Watch run.lock: restart server on every modification
        val watchThread = Thread {
            var lastModified = if (lockFile.exists()) lockFile.lastModified() else 0L
            while (!Thread.currentThread().isInterrupted) {
                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                if (lockFile.exists()) {
                    val modified = lockFile.lastModified()
                    if (modified != lastModified) {
                        lastModified = modified
                        println("[dev] run.lock modified — restarting server… (${java.time.LocalTime.now().let { "%02d:%02d:%02d".format(it.hour, it.minute, it.second) }})")
                        killTree(serverRef.get())
                        serverRef.set(startServer())
                    }
                }
            }
        }
        watchThread.isDaemon = true
        watchThread.start()

        try {
            clientProc.waitFor()
        } finally {
            killTree(serverRef.get())
            watchThread.interrupt()
        }
    }
}