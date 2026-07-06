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
    // WATCH_MODE=0 → skip file watchers; touch run.lock rebuilds everything then restarts server
    val watchMode = System.getenv("WATCH_MODE") != "0"

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
            Thread {
                    runCatching {
                        inputStream.bufferedReader().forEachLine { println("$prefix$it") }
                    }
                }
                .also {
                    it.isDaemon = true
                    it.start()
                },
            Thread {
                    runCatching {
                        errorStream.bufferedReader().forEachLine {
                            System.err.println("$prefix$it")
                        }
                    }
                }
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
        pb.environment()["MICRAFT_WEB_DIST"] = rootDir.resolve("app/webApp/build").absolutePath
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

    fun buildCss() {
        println("[dev] building CSS…")
        runNpm("run", "build:css", "--prefix", "app/webApp/ts-src")
        println("[dev] building map…")
        runNpm("run", "build:map", "--prefix", "app/webApp/ts-src")
        println("[dev] building map CSS…")
        runNpm("run", "build:map:css", "--prefix", "app/webApp/ts-src")
    }

    println("[dev] building server…")
    val serverResult = runGradle(":server:installDist")
    if (serverResult != 0)
        error("[dev] server build failed — fix compilation errors before starting")

    println("[dev] building client…")
    val clientResult = runGradle(":app:webApp:copyResourcesToWebDist")
    if (clientResult != 0)
        error("[dev] client build failed — fix compilation errors before starting")

    buildCss()

    val serverRef = java.util.concurrent.atomic.AtomicReference(startServer())

    val clientProc: Process?
    val cssProc: Process?
    val mapProc: Process?
    val mapCssProc: Process?

    if (watchMode) {
        clientProc =
            ProcessBuilder(
                    gradle, ":app:webApp:copyResourcesToWebDist", "--continuous", "--console=plain")
                .directory(rootDir)
                .start()
        clientProc.pipeOutput("[wasm] ")

        cssProc =
            ProcessBuilder("sh", "-c", "npm run watch:css --prefix app/webApp/ts-src")
                .directory(rootDir)
                .start()
        cssProc.pipeOutput("[css] ")

        mapProc =
            ProcessBuilder("sh", "-c", "npm run watch:map --prefix app/webApp/ts-src")
                .directory(rootDir)
                .start()
        mapProc.pipeOutput("[map] ")

        mapCssProc =
            ProcessBuilder("sh", "-c", "npm run watch:map:css --prefix app/webApp/ts-src")
                .directory(rootDir)
                .start()
        mapCssProc.pipeOutput("[map:css] ")
    } else {
        clientProc = null
        cssProc = null
        mapProc = null
        mapCssProc = null
        println("[dev] WATCH_MODE=0 — watchers disabled; touch run.lock to rebuild all and restart")
    }

    Runtime.getRuntime()
        .addShutdownHook(
            Thread {
                killTree(serverRef.get())
                clientProc?.let { killTree(it) }
                cssProc?.let { killTree(it) }
                mapProc?.let { killTree(it) }
                mapCssProc?.let { killTree(it) }
            })

    // Watch run.lock: on modification rebuild server (and in WATCH_MODE=0 also CSS+client) then
    // restart. Debug mode is preserved across restarts.
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
                    println("⚠\uFE0F=====================================⚠\uFE0F")
                    println(
                        "[dev] run.lock modified — rebuilding… (${java.time.LocalTime.now().let { "%02d:%02d:%02d".format(it.hour, it.minute, it.second) }})")
                    println("⚠\uFE0F=====================================⚠\uFE0F")
                    killTree(serverRef.get())
                    if (!watchMode) {
                        buildCss()
                        runGradle(":app:webApp:copyResourcesToWebDist")
                    }
                    runGradle(":server:installDist")
                    serverRef.set(startServer())
                }
            }
        }
    }
    watchThread.isDaemon = true
    watchThread.start()

    try {
        if (watchMode) {
            clientProc!!.waitFor()
        } else {
            java.util.concurrent.CountDownLatch(1).await()
        }
    } catch (_: InterruptedException) {
        // shutdown
    } finally {
        killTree(serverRef.get())
        watchThread.interrupt()
    }
}

/**
 * ./gradlew dev
 *
 * Builds server + client, then starts both in parallel:
 * - Ktor game server on :8080 — serves API, WebSocket, and the game client static files
 * - wasmJsBrowserDevelopmentWebpack --continuous — recompiles Kotlin/Wasm on change
 *
 * Touching run.lock restarts the server without stopping the watcher. Ctrl+C stops both processes.
 */
tasks.register("dev") {
    group = "micraft"
    description = "Build and start the game server (:8080, serves everything)"
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
 * Then open: http://localhost:8080/?debug&bx=8&by=2&bz=8 Keys 1-6 orbit the camera around each face
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
