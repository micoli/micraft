plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinxSerialization)
}

sourceSets {
    main {
        kotlin {
            srcDir("${rootProject.projectDir}/plugins")
            exclude("**/test/**")
        }
    }
    test {
        kotlin {
            val pluginsDir = rootProject.projectDir.resolve("plugins")
            if (pluginsDir.exists()) {
                pluginsDir.listFiles { f -> f.isDirectory }?.forEach { pluginDir ->
                    val testDir = pluginDir.resolve("test")
                    if (testDir.exists()) srcDir(testDir)
                }
            }
        }
    }
}

tasks.test {
    systemProperty("projectDir", rootProject.projectDir.absolutePath)
}

group = "org.micoli.micraft"
version = "1.0.0"
application {
    mainClass = "org.micoli.micraft.ApplicationKt"
}

dependencies {
    api(projects.core)
    implementation(libs.classgraph)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
