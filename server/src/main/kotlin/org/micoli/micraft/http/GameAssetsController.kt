package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import kotlinx.serialization.Serializable

class GameAssetsController {
    @Serializable
    data class AssetEntry(val pack: String, val name: String, val path: String, val format: String)

    private val extensions = setOf("glb", "gltf", "fbx", "bbmodel")
    private val root = File("resources/game-assets")

    private val contentTypeByExt =
        mapOf(
            "glb" to ContentType("model", "gltf-binary"),
            "gltf" to ContentType("model", "gltf+json"),
            "fbx" to ContentType.Application.OctetStream,
            "bbmodel" to ContentType.Application.Json,
            "png" to ContentType.Image.PNG,
            "jpg" to ContentType.Image.JPEG,
            "jpeg" to ContentType.Image.JPEG,
            "ktx" to ContentType.Application.OctetStream,
            "dds" to ContentType.Application.OctetStream,
            "bin" to ContentType.Application.OctetStream,
        )

    fun register(route: Route) =
        route.apply {
            get(
                "/api/game-assets",
                {
                    description = "3D game asset files discovered under resources/game-assets"
                    response { code(HttpStatusCode.OK) { body<List<AssetEntry>>() } }
                }) {
                    val assets = mutableListOf<AssetEntry>()
                    if (root.exists()) {
                        root
                            .listFiles()
                            ?.filter { it.isDirectory && !it.name.startsWith(".") }
                            ?.forEach { packDir ->
                                packDir
                                    .walkTopDown()
                                    .filter {
                                        it.isFile &&
                                            it.name.extension.lowercase() in extensions &&
                                            !it.path.contains("__MACOSX")
                                    }
                                    .forEach { file ->
                                        val rel =
                                            file
                                                .relativeTo(File("resources"))
                                                .path
                                                .replace("\\", "/")
                                        assets.add(
                                            AssetEntry(
                                                packDir.name,
                                                file.nameWithoutExtension,
                                                rel,
                                                file.name.extension.lowercase()))
                                    }
                            }
                    }
                    val json = buildString {
                        append("[")
                        assets.forEachIndexed { i, a ->
                            if (i > 0) append(",")
                            append(
                                """{"pack":${a.pack.toJson()},"name":${a.name.toJson()},"path":${a.path.toJson()},"format":${a.format.toJson()}}""")
                        }
                        append("]")
                    }
                    call.respondText(json, ContentType.Application.Json)
                }

            get(
                "/api/game-assets/file/{path...}",
                {
                    description = "Raw asset file bytes (glb/gltf/fbx/textures)"
                    request {
                        pathParameter<String>("path") { description = "Relative asset path" }
                    }
                    response {
                        code(HttpStatusCode.OK) {
                            body<ByteArray>() { mediaTypes(ContentType.Application.OctetStream) }
                        }
                        code(HttpStatusCode.NotFound) { description = "Asset not found" }
                    }
                }) {
                    val relPath =
                        call.parameters.getAll("path")?.joinToString("/")
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val file = File("resources/game-assets/$relPath")
                    if (!file.exists() || !file.canonicalPath.startsWith(root.canonicalPath)) {
                        return@get call.respond(HttpStatusCode.NotFound)
                    }
                    val ct =
                        contentTypeByExt[file.extension.lowercase()]
                            ?: ContentType.Application.OctetStream
                    call.respondBytes(file.readBytes(), ct)
                }
        }

    private fun String.toJson() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private val String.extension
        get() = substringAfterLast('.', "")
}
