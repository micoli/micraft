import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

val tsBuild by
    tasks.registering(Exec::class) {
        workingDir = file("ts-src")
        inputs.dir("ts-src")
        outputs.file("src/wasmJsMain/resources/mc_bindings.js")
        commandLine("sh", "-c", "npm install && npm run build")
    }

tasks.matching { it.name == "wasmJsProcessResources" }.configureEach { dependsOn(tsBuild) }

val cleanStaleWasm by
    tasks.registering(Delete::class) {
        delete(fileTree(layout.buildDirectory.dir("web")) { include("*.wasm") })
    }

// Assemble the served dir (build/web) — a dir webpack never cleans. Copies the webpack output
// (webApp.js, .wasm, composeResources) plus static resources (index.html, sw.js, favicon).
// mc_bindings.js and main.css are owned by esbuild/tailwind, which write straight into build/web,
// so they are excluded here to avoid overwriting the fresh bundles with stale source copies.
val copyResourcesToWebDist by
    tasks.registering(Copy::class) {
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
