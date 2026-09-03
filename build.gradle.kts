plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.qodana)
    alias(libs.plugins.spotless)
    alias(libs.plugins.cyclonedx)
}

// ── Supply-chain: lock every resolved dependency version ──────────────────────
allprojects { dependencyLocking { lockAllConfigurations() } }

// Force patched versions of the Kotlin/JS webpack toolchain (kotlin-js-store/yarn.lock).
// Build-time only. Refresh with `./gradlew kotlinUpgradeYarnLock`.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().apply {
        // Only same-major bumps — forcing majors here breaks the webpack build.
        resolution("webpack", "5.104.1")
        resolution("socket.io-parser", "4.2.7")
        resolution("js-yaml", "4.3.2")
        resolution("fast-uri", "3.1.6")
        resolution("body-parser", "1.20.6")
        resolution("brace-expansion", "2.1.4")
        resolution("qs", "6.16.0")
        // Cross-major, but the public API is unchanged and both are consumed via require().
        resolution("serialize-javascript", "7.0.7")
        resolution("diff", "8.0.3")
    }
}

/** Shared logic for the dev and prod tasks. */
fun startDevMode(rootDir: java.io.File, watch: Boolean) {
    val gradle = "${rootDir}/gradlew"
    val serverBin = rootDir.resolve("server/build/install/server/bin/server")
    // Single served dir that webpack never cleans. dev → build/web (assembled by
    // copyResourcesToWebDist + esbuild/tailwind watchers); prod → productionExecutable.
    val webDist =
        if (watch) rootDir.resolve("app/webApp/build/web")
        else rootDir.resolve("app/webApp/build/dist/wasmJs/productionExecutable")

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
        println(
            "[dev] starting server (${java.time.LocalTime.now().let { "%02d:%02d:%02d".format(it.hour, it.minute, it.second) }})")
        val pb = ProcessBuilder(serverBin.absolutePath).directory(rootDir)
        pb.environment()["MICRAFT_WEB_DIST"] = webDist.absolutePath
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

    fun runNpmEnv(env: Map<String, String>, vararg args: String): Int {
        val cmd = (listOf("npm") + args.toList()).joinToString(" ")
        val pb = ProcessBuilder("sh", "-c", cmd).directory(rootDir).redirectErrorStream(false)
        pb.environment().putAll(env)
        val p = pb.start()
        p.pipeOutput("")
        return p.waitFor()
    }

    // App CSS goes straight into the served dir; map CSS/JS go to server resources.
    fun buildClientCss() {
        println("[dev] building CSS…")
        runNpmEnv(
            mapOf("MC_OUT_CSS" to webDist.resolve("main.css").absolutePath),
            "run",
            "build:css",
            "--prefix",
            "app/webApp/ts-src")
    }
    fun buildMap() {
        println("[dev] building map…")
        runNpm("run", "build:map", "--prefix", "app/webApp/ts-src")
        runNpm("run", "build:map:css", "--prefix", "app/webApp/ts-src")
    }

    println("[dev] building server…")
    if (runGradle(":server:installDist") != 0)
        error("[dev] server build failed — fix compilation errors before starting")

    println("[dev] building client…")
    if (watch) {
        // Assembles build/web: webpack output (webApp.js, .wasm) + static (index.html, sw.js…).
        if (runGradle(":app:webApp:copyResourcesToWebDist") != 0)
            error("[dev] client build failed — fix compilation errors before starting")
    } else {
        // Production distribution assembles wasm + static resources into webDist.
        if (runGradle(":app:webApp:wasmJsBrowserDistribution") != 0)
            error("[prod] client build failed — fix compilation errors before starting")
    }

    // App bundle + CSS straight into the served dir (overwrites any stale resource copies).
    val jsOut = webDist.resolve("mc_bindings.js").absolutePath
    runNpmEnv(
        mapOf("MC_OUT_JS" to jsOut, "MC_ESBUILD_FLAGS" to if (watch) "" else "--minify"),
        "run",
        "build",
        "--prefix",
        "app/webApp/ts-src")
    buildClientCss()
    buildMap()

    val serverRef = java.util.concurrent.atomic.AtomicReference(startServer())

    val watchers = mutableListOf<Process>()
    if (watch) {
        fun watcher(prefix: String, cmd: String, env: Map<String, String> = emptyMap()) {
            val pb = ProcessBuilder("sh", "-c", cmd).directory(rootDir)
            pb.environment().putAll(env)
            val p = pb.start()
            p.pipeOutput(prefix)
            watchers += p
        }
        // Kotlin/Wasm client — continuous recompile; copyResourcesToWebDist restores webApp.js +
        // static into build/web after each rebuild (webpack cleans its own output dir, not
        // build/web).
        watcher(
            "[wasm] ", "$gradle :app:webApp:copyResourcesToWebDist --continuous --console=plain")
        // App TS bundle + CSS — esbuild/tailwind --watch straight into the served dir.
        watcher("[js] ", "npm run watch --prefix app/webApp/ts-src", mapOf("MC_OUT_JS" to jsOut))
        watcher(
            "[css] ",
            "npm run watch:css --prefix app/webApp/ts-src",
            mapOf("MC_OUT_CSS" to webDist.resolve("main.css").absolutePath))
        watcher("[map] ", "npm run watch:map --prefix app/webApp/ts-src")
        watcher("[map:css] ", "npm run watch:map:css --prefix app/webApp/ts-src")
        watcher("[admin] ", "npm run watch:admin --prefix app/webApp/ts-src")
        watcher("[admin:css] ", "npm run watch:admin:css --prefix app/webApp/ts-src")
    } else {
        println("[prod] serving optimized build; no watchers")
    }

    Runtime.getRuntime()
        .addShutdownHook(
            Thread {
                killTree(serverRef.get())
                watchers.forEach { killTree(it) }
            })

    try {
        if (watch) watchers.first().waitFor() else java.util.concurrent.CountDownLatch(1).await()
    } catch (_: InterruptedException) {
        // shutdown
    } finally {
        killTree(serverRef.get())
    }
}

/**
 * ./gradlew dev
 *
 * Builds server + client, then starts both with live watchers:
 * - Ktor game server on :8080 — serves API, WebSocket, and the game client static files
 * - wasmJsBrowserDevelopmentWebpack --continuous — recompiles Kotlin/Wasm on change
 * - esbuild/tailwind --watch — rebuild the TS bundle + CSS straight into the served dir
 *
 * Any client change updates the served dir; the server's asset-hash poll pushes a version over /ws
 * and the service worker refreshes. Ctrl+C stops everything; re-run the task to rebuild + restart
 * the server.
 */
tasks.register("dev") {
    group = "micraft"
    description = "Build and start the game server with live client watchers (:8080)"
    notCompatibleWithConfigurationCache("Launches external processes via script-level function")
    val rootDir = rootProject.projectDir
    doLast { startDevMode(rootDir, watch = true) }
}

/**
 * ./gradlew prod
 *
 * Builds optimized bundles (production wasm, esbuild --minify, tailwind --minify) into the served
 * production dir and starts the server — no watchers, no live restart.
 */
tasks.register("prod") {
    group = "micraft"
    description = "Build optimized bundles and start the game server (:8080, no watchers)"
    notCompatibleWithConfigurationCache("Launches external processes via script-level function")
    val rootDir = rootProject.projectDir
    doLast { startDevMode(rootDir, watch = false) }
}

spotless {
    isEnforceCheck = false

    // `-PspotlessRatchet=<git-ref>` restricts formatting to files changed since that ref, which is
    // what `make quick-code-standard` uses to stay fast. Unset = format everything.
    (findProperty("spotlessRatchet") as String?)?.let { ratchetFrom(it) }

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
