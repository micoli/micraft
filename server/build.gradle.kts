import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
}

tasks.named<ProcessResources>("processResources") {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    doLast {
        val ts =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH.mm.ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now())
        File(destinationDir, "server-build-timestamp.txt").writeText(ts)
    }
}

sourceSets {
    main {
        kotlin {
            val pluginsDir = rootProject.projectDir.resolve("plugins")
            if (pluginsDir.exists()) {
                pluginsDir
                    .listFiles { f -> f.isDirectory }
                    ?.forEach { pluginDir ->
                        val serverDir = pluginDir.resolve("server")
                        if (serverDir.exists()) srcDir(serverDir)
                    }
            }
        }
    }
    test {
        kotlin {
            val pluginsDir = rootProject.projectDir.resolve("plugins")
            if (pluginsDir.exists()) {
                pluginsDir
                    .listFiles { f -> f.isDirectory }
                    ?.forEach { pluginDir ->
                        val testDir = pluginDir.resolve("test")
                        if (testDir.exists()) srcDir(testDir)
                    }
            }
        }
    }
}

val rootDirPath: String = rootProject.projectDir.absolutePath

tasks.test {
    systemProperty("projectDir", rootDirPath)
    workingDir = rootProject.projectDir
    environment("MICRAFT_WORLD_NAME", "test_world")
    jvmArgs("-Xmx512m")
    val testWorldDir = rootProject.projectDir.resolve("data/world/test_world")
    doFirst { testWorldDir.deleteRecursively() }
    //    testLogging {
    //        events("passed", "skipped", "failed")
    //        showStandardStreams = true
    //    }
}

tasks.register<JavaExec>("addUser") {
    group = "application"
    description = "Add a local auth user. Args: <email> <password> [displayName]"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.micoli.micraft.auth.AddUserCliKt")
    workingDir = rootProject.projectDir
    if (project.hasProperty("args")) {
        args = (project.property("args") as String).split(" ")
    }
}

tasks.register<JavaExec>("validateConfig") {
    group = "verification"
    description =
        "Validate all YAML config files in resources/ and data/config/. Reports all errors."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.micoli.micraft.config.ValidateConfigCliKt")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("extractKayKitAnimations") {
    group = "tools"
    description =
        "Extract KayKit GLB animations into player bbmodel files (merges by animation name: " +
            "same-name animations are overwritten, hand-authored ones are kept)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.micoli.micraft.tools.ExtractKayKitAnimationsKt")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("generateCommandsDocs") {
    group = "documentation"
    description = "Regenerates the slash commands section in README.md."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.micoli.micraft.tools.GenerateCommandsDocsKt")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("checkCommandsDocs") {
    group = "verification"
    description = "Fails if README.md commands section is out of date."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.micoli.micraft.tools.GenerateCommandsDocsKt")
    workingDir = rootProject.projectDir
    args("--check")
}

group = "org.micoli.micraft"

version = "1.0.0"

application { mainClass = "org.micoli.micraft.ApplicationKt" }

tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }

dependencies {
    api(projects.core)
    implementation(kotlin("reflect"))
    implementation(libs.bcrypt)
    implementation(libs.classgraph)
    implementation(libs.logback)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.kaml)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.json.schema.validator)
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp.compiler)
    implementation(libs.commons.jexl3)
    implementation(libs.java.jwt)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
}
