package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

// Excluded from the OpenAPI spec by the Application.kt pathFilter (docs.html/docs.js are markup
// and a JS bundle, not a JSON API) — no need for the documented get/post DSL here.
class DocsController {
    private val staticDir =
        System.getenv("MICRAFT_MAP_STATIC_DIR") ?: System.getenv("MICRAFT_WEB_DIST")

    private fun readStaticResource(name: String): String {
        // docs.js is esbuild output written straight into $MICRAFT_WEB_DIST by `make build-docs`
        // — read from there (like map.js/admin.js) instead of the classpath, which only ever
        // holds a stale copy nothing regenerates.
        if (staticDir != null) {
            val f = File(staticDir, name)
            if (f.exists()) return f.readText()
        }
        return Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream(name)!!
            .bufferedReader()
            .readText()
    }

    fun register(route: Route) =
        route.apply {
            get("/api/docs") {
                if (staticDir != null)
                    call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                call.respondText(readStaticResource("docs.html"), ContentType.Text.Html)
            }
            get("/docs.js") {
                if (staticDir != null)
                    call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                call.respondText(readStaticResource("docs.js"), ContentType.Text.JavaScript)
            }
        }
}
