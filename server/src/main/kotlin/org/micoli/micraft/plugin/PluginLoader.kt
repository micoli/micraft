package org.micoli.micraft.plugin

import io.github.classgraph.ClassGraph
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(PluginLoader::class.java)

object PluginLoader {
    fun load(jarPluginsDir: Path = Path.of("jar-plugins")): List<PluginEntrypoint> {
        if (!jarPluginsDir.exists()) return emptyList()
        val jars = jarPluginsDir.listDirectoryEntries("*.jar")
        if (jars.isEmpty()) return emptyList()

        val classLoader =
            URLClassLoader(
                jars.map { it.toUri().toURL() }.toTypedArray(),
                Thread.currentThread().contextClassLoader,
            )

        return ClassGraph()
            .enableClassInfo()
            .overrideClasspath(jars.map { it.toAbsolutePath().toString() })
            .addClassLoader(classLoader)
            .scan()
            .use { result ->
                result
                    .getClassesImplementing(PluginEntrypoint::class.java.name)
                    .filter { !it.isAbstract && !it.isInterface }
                    .mapNotNull { info ->
                        runCatching {
                                @Suppress("UNCHECKED_CAST")
                                (classLoader.loadClass(info.name) as Class<PluginEntrypoint>)
                                    .getDeclaredConstructor()
                                    .newInstance()
                            }
                            .onFailure { e ->
                                log.warn(
                                    "Failed to load plugin entrypoint {}: {}", info.name, e.message)
                            }
                            .getOrNull()
                    }
            }
    }
}
