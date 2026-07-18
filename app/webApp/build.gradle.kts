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
                    "node_modules/**",
                    ".storybook/**",
                    ".stories/**",
                    "__tests__/**",
                    "storybook-static/**",
                    "**/*.stories.tsx",
                    "**/*.stories.ts",
                    "**/*.test.tsx",
                    "**/*.test.ts",
                    "**/*.spec.tsx",
                    "**/*.spec.ts"
                )
            }
        )
        outputs.file("src/wasmJsMain/resources/mc_bindings.js")
        commandLine("sh", "-c", "npm install && npm run build")
    }

tasks.matching { it.name == "wasmJsProcessResources" }.configureEach { dependsOn(tsBuild) }

val generateBuildConfig by
    tasks.registering {
        val genDir = File(projectDir, buildConfigGenDir)
        val outFile = File(genDir, "BuildConfig.kt")
        outputs.file(outFile)
        // Always re-run so the timestamp is current, but the minute guard below prevents
        // content from changing within a single compile cycle (~15 s), breaking the watch loop.
        outputs.upToDateWhen { false }
        doLast {
            val ts =
                DateTimeFormatter.ofPattern("yyyyMMdd-HH.mm.ss")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now())
            val content = "package org.micoli.micraft\n\nconst val BUILD_TIMESTAMP = \"$ts\"\n"
            val existingTs =
                if (outFile.exists()) outFile.readText().substringAfter("\"").substringBefore("\"")
                else ""
            if (existingTs.take(14) != ts.take(14)) {
                genDir.mkdirs()
                outFile.writeText(content)
            }
        }
    }

tasks.matching { it.name == "compileKotlinWasmJs" }.configureEach { dependsOn(generateBuildConfig) }

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
