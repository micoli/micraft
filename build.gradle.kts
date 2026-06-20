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
 * Shared logic for dev and devDebug tasks.
 *
 * @param debugWorld  true → MICRAFT_DEBUG_WORLD=1 is passed to the server process,
 *                    which activates DebugChunkGenerator + fly spawn near the test block.
 */
fun startDevMode(rootDir: java.io.File, debugWorld: Boolean) {
    val gradle    = "${rootDir}/gradlew"
    val lockFile  = rootDir.resolve("run.lock")
    val serverBin = rootDir.resolve("server/build/install/server/bin/server")

    fun killTree(p: Process) {
        p.descendants().forEach { it.destroyForcibly() }
        p.destroyForcibly()
        p.waitFor()
    }

    fun startServer(): Process {
        val tag = if (debugWorld) " [DEBUG WORLD]" else ""
        println("[dev] starting server$tag (${java.time.LocalTime.now().let { "%02d:%02d:%02d".format(it.hour, it.minute, it.second) }})")
        val pb = ProcessBuilder(serverBin.absolutePath)
            .directory(rootDir)
            .inheritIO()
        if (debugWorld) pb.environment()["MICRAFT_DEBUG_WORLD"] = "1"
        return pb.start()
    }

    // Force full build of server and client on every start
    println("[dev] building server…")
    ProcessBuilder(gradle, ":server:installDist", "--rerun-tasks")
        .directory(rootDir).inheritIO().start().waitFor()

    println("[dev] building client…")
    ProcessBuilder(gradle, ":app:webApp:wasmJsDevelopmentExecutableCompileSync", "--rerun-tasks")
        .directory(rootDir).inheritIO().start().waitFor()

    val serverRef  = java.util.concurrent.atomic.AtomicReference(startServer())
    val clientProc = ProcessBuilder(gradle, ":app:webApp:wasmJsBrowserDevelopmentRun", "--continuous")
        .directory(rootDir).inheritIO().start()

    Runtime.getRuntime().addShutdownHook(Thread {
        killTree(serverRef.get())
        killTree(clientProc)
    })

    // Watch run.lock: restart server on every modification (debug mode is preserved across restarts)
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

/**
 * ./gradlew dev
 *
 * Builds server + client, then starts both in parallel:
 *   - Ktor game server  on :8080  (ProceduralChunkGenerator)
 *   - Webpack dev server on :8081 (hot-reload)
 *
 * Touching run.lock restarts the server without stopping the client.
 * Ctrl+C stops both processes.
 */
tasks.register("dev") {
    group = "micraft"
    description = "Build and start the game server (:8080) and the webpack dev server (:8081)"
    doLast { startDevMode(rootProject.projectDir, debugWorld = false) }
}

/**
 * ./gradlew devDebug
 *
 * Same as dev but the server runs with MICRAFT_DEBUG_WORLD=1:
 *   - DebugChunkGenerator: single GRASS block at world (8, 2, 8)
 *   - Player spawns at (8, 1, 14) in fly mode, facing the block
 *
 * Then open: http://localhost:8081/?debug&bx=8&by=2&bz=8
 * Keys 1-6 orbit the camera around each face of the block.
 * Escape releases the camera lock.
 * run.lock still works to restart the server (debug mode is preserved).
 */
tasks.register("devDebug") {
    group = "micraft"
    description = "Same as dev but with a single-block debug world for texture inspection"
    doLast { startDevMode(rootProject.projectDir, debugWorld = true) }
}
