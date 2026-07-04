plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    id("org.jetbrains.qodana") version "2024.3.4"
    id("com.diffplug.spotless") version "7.2.1"
}

/**
 * Shared logic for dev and devDebug tasks.
 *
 * @param debugWorld true → MICRAFT_DEBUG_WORLD=1 is passed to the server process, which activates
 *   DebugChunkGenerator + fly spawn near the test block.
 */
fun startDevMode(rootDir: java.io.File, debugWorld: Boolean) {
    val gradle = "${rootDir}/gradlew"
    val lockFile = rootDir.resolve("run.lock")
    val serverBin = rootDir.resolve("server/build/install/server/bin/server")

    fun killTree(p: Process) {
        p.descendants().forEach { it.destroy() }
        p.destroy()
        if (!p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
            p.descendants().forEach { it.destroyForcibly() }
            p.destroyForcibly()
            p.waitFor()
        }
    }

    // Pipe stdout+stderr of a process through Gradle's logger so they appear in the console
    // even when Gradle runs in rich/interactive mode (which wraps System.out).
    fun Process.pipeOutput(prefix: String): List<Thread> =
        listOf(
            Thread { inputStream.bufferedReader().forEachLine { println("$prefix$it") } }
                .also {
                    it.isDaemon = true
                    it.start()
                },
            Thread { errorStream.bufferedReader().forEachLine { System.err.println("$prefix$it") } }
                .also {
                    it.isDaemon = true
                    it.start()
                },
        )

    fun runGradle(vararg args: String): Int {
        val p =
            ProcessBuilder(gradle, *args, "--console=plain")
                .directory(rootDir)
                .redirectErrorStream(false)
                .start()
        p.pipeOutput("")
        return p.waitFor()
    }

    fun startServer(): Process {
        val tag = if (debugWorld) " [DEBUG WORLD]" else ""
        println(
            "[dev] starting server$tag (${java.time.LocalTime.now().let { "%02d:%02d:%02d".format(it.hour, it.minute, it.second) }})")
        val pb = ProcessBuilder(serverBin.absolutePath).directory(rootDir)
        if (debugWorld) pb.environment()["MICRAFT_DEBUG_WORLD"] = "1"
        val p = pb.start()
        p.pipeOutput("[server] ")
        return p
    }

    fun runNpm(vararg args: String): Int {
        val cmd = (listOf("npm") + args.toList()).joinToString(" ")
        val p =
            ProcessBuilder("sh", "-c", cmd).directory(rootDir).redirectErrorStream(false).start()
        p.pipeOutput("")
        return p.waitFor()
    }

    println("[dev] building server…")
    val serverResult = runGradle(":server:installDist")
    if (serverResult != 0)
        error("[dev] server build failed — fix compilation errors before starting")

    println("[dev] building client…")
    val clientResult = runGradle(":app:webApp:wasmJsDevelopmentExecutableCompileSync")
    if (clientResult != 0)
        error("[dev] client build failed — fix compilation errors before starting")

    println("[dev] building CSS…")
    val cssResult = runNpm("run", "build:css", "--prefix", "app/webApp/ts-src")
    if (cssResult != 0) error("[dev] CSS build failed")

    println("[dev] building map…")
    val mapResult = runNpm("run", "build:map", "--prefix", "app/webApp/ts-src")
    if (mapResult != 0) error("[dev] map build failed")

    println("[dev] building map CSS…")
    val mapCssResult = runNpm("run", "build:map:css", "--prefix", "app/webApp/ts-src")
    if (mapCssResult != 0) error("[dev] map CSS build failed")

    val serverRef = java.util.concurrent.atomic.AtomicReference(startServer())
    val clientProc =
        ProcessBuilder(
                gradle,
                ":app:webApp:wasmJsBrowserDevelopmentRun",
                "--continuous",
                "--console=plain")
            .directory(rootDir)
            .start()
    clientProc.pipeOutput("[webpack] ")

    val cssProc =
        ProcessBuilder("sh", "-c", "npm run watch:css --prefix app/webApp/ts-src")
            .directory(rootDir)
            .start()
    cssProc.pipeOutput("[css] ")

    val mapProc =
        ProcessBuilder("sh", "-c", "npm run watch:map --prefix app/webApp/ts-src")
            .directory(rootDir)
            .start()
    mapProc.pipeOutput("[map] ")

    val mapCssProc =
        ProcessBuilder("sh", "-c", "npm run watch:map:css --prefix app/webApp/ts-src")
            .directory(rootDir)
            .start()
    mapCssProc.pipeOutput("[map:css] ")

    Runtime.getRuntime()
        .addShutdownHook(
            Thread {
                killTree(serverRef.get())
                killTree(clientProc)
                killTree(cssProc)
                killTree(mapProc)
                killTree(mapCssProc)
            })

    // Watch run.lock: restart server on every modification (debug mode is preserved across
    // restarts)
    val watchThread = Thread {
        var lastModified = if (lockFile.exists()) lockFile.lastModified() else 0L
        while (!Thread.currentThread().isInterrupted) {
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
                break
            }
            if (lockFile.exists()) {
                val modified = lockFile.lastModified()
                if (modified != lastModified) {
                    lastModified = modified
                    println(
                        "[dev] run.lock modified — rebuilding server… (${java.time.LocalTime.now().let { "%02d:%02d:%02d".format(it.hour, it.minute, it.second) }})")
                    killTree(serverRef.get())
                    runGradle(":server:installDist")
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
 * - Ktor game server on :8080 (ProceduralChunkGenerator)
 * - Webpack dev server on :8081 (hot-reload)
 *
 * Touching run.lock restarts the server without stopping the client. Ctrl+C stops both processes.
 */
tasks.register("dev") {
    group = "micraft"
    description = "Build and start the game server (:8080) and the webpack dev server (:8081)"
    notCompatibleWithConfigurationCache("Launches external processes via script-level function")
    val rootDir = rootProject.projectDir
    doLast { startDevMode(rootDir, debugWorld = false) }
}

/**
 * ./gradlew devDebug
 *
 * Same as dev but the server runs with MICRAFT_DEBUG_WORLD=1:
 * - DebugChunkGenerator: single GRASS block at world (8, 2, 8)
 * - Player spawns at (8, 1, 14) in fly mode, facing the block
 *
 * Then open: http://localhost:8081/?debug&bx=8&by=2&bz=8 Keys 1-6 orbit the camera around each face
 * of the block. Escape releases the camera lock. run.lock still works to restart the server (debug
 * mode is preserved).
 */
tasks.register("devDebug") {
    group = "micraft"
    description = "Same as dev but with a single-block debug world for texture inspection"
    notCompatibleWithConfigurationCache("Launches external processes via script-level function")
    val rootDir = rootProject.projectDir
    doLast { startDevMode(rootDir, debugWorld = true) }
}

spotless {
    isEnforceCheck = false

    kotlin {
        target("**/*.kt", "**/*.kts")
        ktfmt("0.51").kotlinlangStyle()
    }

    format("misc") {
        target(
            "server/**/*.yml",
            "server/**/*.yaml",
            "app/**/*.yml",
            "app/**/*.yaml",
            "core/**/*.yml",
            "core/**/*.yaml",
            "resources/**/*.yml",
            "resources/**/*.yaml")
        trimTrailingWhitespace()
        leadingTabsToSpaces(2)
        endWithNewline()
    }
}
