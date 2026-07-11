package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

class AssetManifestController(private val webBuildDir: String?) {
    private data class CachedManifest(val computedAt: Long, val entries: Map<String, String>)

    private val cache = AtomicReference<CachedManifest?>(null)

    private fun md5(file: File): String {
        val bytes = file.readBytes()
        return MessageDigest.getInstance("MD5")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }

    private fun scanEntries(dir: File): Map<String, String> =
        dir.listFiles { f -> f.isFile && f.extension in listOf("js", "wasm", "css") }
            .orEmpty()
            .sortedBy { it.name }
            .associate { it.name to md5(it) }

    private fun getOrCompute(dir: File): Map<String, String> {
        val now = System.currentTimeMillis()
        val cached = cache.get()
        if (cached != null && now - cached.computedAt < 2000L) return cached.entries
        val entries = scanEntries(dir)
        cache.set(CachedManifest(now, entries))
        return entries
    }

    // MICRAFT_WEB_DIST points directly at the served executable dir. Legacy fallback: if it points
    // at the module build dir, descend into the dev executable subdir.
    private fun resolveDir(): File? {
        val base = webBuildDir?.let { File(it) }?.takeIf { it.isDirectory } ?: return null
        if (File(base, "index.html").exists()) return base
        return File(base, "kotlin-webpack/wasmJs/developmentExecutable").takeIf { it.isDirectory }
            ?: base
    }

    /** Stable sorted signature of all asset hashes, or null when no dist dir is available. */
    fun signature(): String? =
        resolveDir()?.let { dir ->
            getOrCompute(dir).entries.joinToString(",") { "${it.key}:${it.value}" }
        }

    fun invalidateCache() = cache.set(null)

    fun register(route: Route) =
        route.apply {
            get("/api/assets/manifest") {
                call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                val dir = resolveDir()
                if (dir == null) {
                    call.respondText("{}", ContentType.Application.Json)
                    return@get
                }
                val json =
                    "{\n  " +
                        getOrCompute(dir).entries.joinToString(",\n  ") {
                            "\"${it.key}\": \"${it.value}\""
                        } +
                        "\n}"
                call.respondText(json, ContentType.Application.Json)
            }
        }
}
