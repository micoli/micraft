import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
}

kotlin {
    jvm()

    js { browser() }

    @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }

    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.serialization.protobuf)
                api(libs.kotlinx.coroutinesCore)
            }
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
        val jvmTest by getting { dependencies { implementation(kotlin("reflect")) } }
    }
}

dependencies { add("kspCommonMainMetadata", project(":codec-processor")) }

// KSP only runs on the common metadata compilation; every downstream compilation must wait for it.
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}

// Per-target KSP tasks aren't KotlinCompilationTask themselves but also consume the generated
// commonMain metadata sources, so Gradle 9.7+'s implicit-dependency validation flags them too.
tasks
    .matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }
    .configureEach { dependsOn("kspCommonMainKotlinMetadata") }
