import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

val buildConfigGenDir = "build/generated/buildConfig/wasmJsMain/kotlin"

val tsBuild by
    tasks.registering(Exec::class) {
        workingDir = file("ts-src")
        inputs.files(
            fileTree("ts-src") {
                exclude(
                    "package.json",
                    // Regenerated with a fresh timestamp by `npm run build` itself: leaving it in
                    // the inputs makes this task invalidate itself on every single build.
                    "buildConfig.ts",
                    "node_modules/**",
                    ".storybook/**",
                    ".stories/**",
                    "__tests__/**",
                    "map/**",
                    "storybook-static/**",
                    "**/*.stories.tsx",
                    "**/*.stories.ts",
                    "**/*.test.tsx",
                    "**/*.test.ts",
                    "**/*.spec.tsx",
                    "**/*.spec.ts")
            })
        outputs.file("src/wasmJsMain/resources/mc_bindings.js")
        commandLine("sh", "-c", "npm install && npm run build")
    }

tasks.matching { it.name == "wasmJsProcessResources" }.configureEach { dependsOn(tsBuild) }

val generateBuildConfig by
    tasks.registering {
        val genDir = File(projectDir, buildConfigGenDir)
        val outFile = File(genDir, "BuildConfig.kt")
        // Track the sources the timestamp is meant to describe, so it is refreshed exactly when
        // there is a new build to stamp. Regenerating unconditionally would rewrite BuildConfig.kt
        // on every invocation and force a full Kotlin/Wasm recompile even with no code change.
        inputs.files(
            fileTree("src/wasmJsMain/kotlin"), fileTree("${rootProject.projectDir}/core/src"))
        outputs.file(outFile)
        // Identical sources can still come from different checkouts; a restored cache entry would
        // carry someone else's timestamp.
        outputs.cacheIf { false }
        doLast {
            val ts =
                DateTimeFormatter.ofPattern("yyyyMMdd-HH.mm.ss")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now())
            genDir.mkdirs()
            outFile.writeText("package org.micoli.micraft\n\nconst val BUILD_TIMESTAMP = \"$ts\"\n")
        }
    }

tasks.matching { it.name == "compileKotlinWasmJs" }.configureEach { dependsOn(generateBuildConfig) }

// The WASM binary is ~6 MB; attempting to cache it causes pack corruption errors.
tasks
    .matching { it.name == "compileDevelopmentExecutableKotlinWasmJs" }
    .configureEach { outputs.cacheIf { false } }

val cleanStaleWasm by
    tasks.registering(Delete::class) {
        delete(fileTree(layout.buildDirectory.dir("web")) { include("*.wasm") })
    }

// Copies only static assets (index.html, sw.js, favicon…) into build/web without compiling WASM
// or running npm. Used at server startup so index.html is present before the first request.
val copyStaticToWebDist by
    tasks.registering(Copy::class) {
        doNotTrackState("build/web is a shared output directory")
        from("src/wasmJsMain/resources") { exclude("mc_bindings.js", "main.css") }
        into(layout.buildDirectory.dir("web"))
    }

// Assemble the served dir (build/web) — a dir webpack never cleans. Copies the webpack output
// (webApp.js, .wasm, composeResources) plus static resources (index.html, sw.js, favicon).
// mc_bindings.js and main.css are owned by esbuild/tailwind, which write straight into build/web,
// so they are excluded here to avoid overwriting the fresh bundles with stale source copies.
val copyResourcesToWebDist by
    tasks.registering(Copy::class) {
        // build/web is a shared dir written by esbuild/tailwind watchers too;
        // disable stale-output cleanup so Gradle doesn't wipe mc_bindings.js / main.css on re-run.
        doNotTrackState("build/web is a shared output directory")
        dependsOn("wasmJsBrowserDevelopmentWebpack", cleanStaleWasm)
        from(layout.buildDirectory.dir("kotlin-webpack/wasmJs/developmentExecutable"))
        from(layout.buildDirectory.dir("processedResources/wasmJs/main")) {
            exclude("mc_bindings.js", "main.css")
        }
        into(layout.buildDirectory.dir("web"))
    }

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        compilerOptions { optIn.add("kotlin.js.ExperimentalWasmJsInterop") }
        browser {}
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            kotlin.srcDir(buildConfigGenDir)
            dependencies {
                implementation(projects.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.js)
                implementation(libs.kotlinx.serialization.protobuf)
            }
        }
    }
}
