import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

val copyBbmodels by
    tasks.registering(Copy::class) {
        into("src/wasmJsMain/resources/models")
        from(rootProject.file("resources")) { include("*.bbmodel") }
        from(rootProject.file("resources/blocks")) {
            include("*.bbmodel")
            into("blocks")
        }
    }

val tsBuild by
    tasks.registering(Exec::class) {
        workingDir = file("ts-src")
        inputs.dir("ts-src")
        outputs.file("src/wasmJsMain/resources/mc_bindings.js")
        commandLine("sh", "-c", "npm install && npm run build")
    }

tasks
    .matching { it.name == "wasmJsProcessResources" }
    .configureEach {
        dependsOn(copyBbmodels)
        dependsOn(tsBuild)
    }

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        compilerOptions { optIn.add("kotlin.js.ExperimentalWasmJsInterop") }
        browser {
            commonWebpackConfig {
                devServer =
                    devServer?.copy(open = false)
                        ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
                            .DevServer(open = false)
            }
        }
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
