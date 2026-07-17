plugins {
    alias(libs.plugins.kotlinJvm)
    id("com.gradleup.shadow")
}

group = "org.micoli.micraft.examples"

version = "1.0.0"

dependencies {
    compileOnly(projects.server)
    implementation(libs.logback)
}

tasks.shadowJar {
    archiveFileName.set("hello-world.jar")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("jar-plugins").asFile)
}
