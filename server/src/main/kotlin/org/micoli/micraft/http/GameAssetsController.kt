package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable

class GameAssetsController {
    @Serializable
    data class AssetEntry(val pack: String, val name: String, val path: String, val format: String)

    @Serializable data class BlendPreviewResponse(val path: String)

    private val extensions = setOf("glb", "gltf", "fbx", "bbmodel", "blend")
    private val root = File("resources/game-assets")

    private val contentTypeByExt =
        mapOf(
            "glb" to ContentType("model", "gltf-binary"),
            "gltf" to ContentType("model", "gltf+json"),
            "fbx" to ContentType.Application.OctetStream,
            "bbmodel" to ContentType.Application.Json,
            "blend" to ContentType.Application.OctetStream,
            "obj" to ContentType.Text.Plain,
            "mtl" to ContentType.Text.Plain,
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

            get(
                "/api/game-assets/blend-preview/{path...}",
                {
                    description =
                        "Converts a .blend file to OBJ/MTL via headless Blender (cached) and returns the OBJ asset path"
                    request {
                        pathParameter<String>("path") {
                            description = "Relative path to a .blend asset"
                        }
                    }
                    response {
                        code(HttpStatusCode.OK) { body<BlendPreviewResponse>() }
                        code(HttpStatusCode.NotFound) { description = "Asset not found" }
                        code(HttpStatusCode.InternalServerError) {
                            description = "Blender conversion failed"
                        }
                    }
                }) {
                    val relPath =
                        call.parameters.getAll("path")?.joinToString("/")
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val file = File("resources/game-assets/$relPath")
                    if (!file.exists() ||
                        !file.canonicalPath.startsWith(root.canonicalPath) ||
                        file.extension.lowercase() != "blend") {
                        return@get call.respond(HttpStatusCode.NotFound)
                    }

                    val hash =
                        MessageDigest.getInstance("SHA-256")
                            .digest(relPath.toByteArray())
                            .joinToString("") { "%02x".format(it) }
                            .take(16)
                    val cacheDir = File(root, ".cache/blend-obj/$hash")
                    val objFile = File(cacheDir, "model.obj")

                    if (!objFile.exists()) {
                        cacheDir.mkdirs()
                        val scriptFile = File(cacheDir, "convert.py")
                        scriptFile.writeText(
                            """
                            import bpy, sys
                            out = sys.argv[sys.argv.index("--") + 1]
                            try:
                                bpy.ops.wm.obj_export(filepath=out, export_materials=True)
                            except AttributeError:
                                bpy.ops.export_scene.obj(filepath=out, use_materials=True)
                            """
                                .trimIndent())

                        val process =
                            ProcessBuilder(
                                    "blender",
                                    "-b",
                                    file.absolutePath,
                                    "--python",
                                    scriptFile.absolutePath,
                                    "--",
                                    objFile.absolutePath)
                                .redirectErrorStream(true)
                                .start()
                        val output = process.inputStream.bufferedReader().readText()
                        val exited = process.waitFor(60, TimeUnit.SECONDS)
                        if (!exited) process.destroyForcibly()
                        if (!exited || process.exitValue() != 0 || !objFile.exists()) {
                            cacheDir.deleteRecursively()
                            return@get call.respond(
                                HttpStatusCode.InternalServerError, output.takeLast(2000))
                        }
                    }

                    val objRelPath = objFile.relativeTo(root).path.replace("\\", "/")
                    call.respondText(
                        """{"path":${objRelPath.toJson()}}""", ContentType.Application.Json)
                }
        }

    private fun String.toJson() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private val String.extension
        get() = substringAfterLast('.', "")
}
