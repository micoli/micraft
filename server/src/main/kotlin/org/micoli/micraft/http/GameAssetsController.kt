package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import org.micoli.micraft.tools.ObjToBbmodel

class GameAssetsController {
    @Serializable
    data class AssetEntry(val pack: String, val name: String, val path: String, val format: String)

    @Serializable data class BlendPreviewResponse(val path: String)

    @Serializable data class BbmodelExportResponse(val path: String)

    @Serializable
    data class BlendSceneNode(
        val name: String,
        val type: String,
        val objType: String? = null,
        val children: List<BlendSceneNode> = emptyList(),
    )

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
                "/api/game-assets/blend-scene/{path...}",
                {
                    description =
                        "Reads a .blend file's collection/object tree via headless Blender (cached)"
                    request {
                        pathParameter<String>("path") {
                            description = "Relative path to a .blend asset"
                        }
                    }
                    response {
                        code(HttpStatusCode.OK) { body<BlendSceneNode>() }
                        code(HttpStatusCode.NotFound) { description = "Asset not found" }
                        code(HttpStatusCode.InternalServerError) {
                            description = "Blender scene read failed"
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

                    val hash = sha256(relPath)
                    val cacheDir = File(root, ".cache/blend-scene/$hash")
                    val sceneFile = File(cacheDir, "scene.json")

                    if (!sceneFile.exists()) {
                        cacheDir.mkdirs()
                        val scriptFile = File(cacheDir, "dump_scene.py")
                        scriptFile.writeText(
                            """
                            import bpy, json, sys

                            out = sys.argv[sys.argv.index("--") + 1]

                            def walk(coll):
                                return {
                                    "name": coll.name,
                                    "type": "collection",
                                    "children": [walk(c) for c in coll.children]
                                    + [
                                        {"name": o.name, "type": "object", "objType": o.type, "children": []}
                                        for o in coll.objects
                                    ],
                                }

                            with open(out, "w") as f:
                                json.dump(walk(bpy.context.scene.collection), f)
                            """
                                .trimIndent())

                        // Write straight to sceneFile rather than scraping stdout — Blender
                        // interleaves its own log lines with the process's stdout unpredictably.
                        val process =
                            ProcessBuilder(
                                    "blender",
                                    "-b",
                                    file.absolutePath,
                                    "--python",
                                    scriptFile.absolutePath,
                                    "--",
                                    sceneFile.absolutePath)
                                .redirectErrorStream(true)
                                .start()
                        val output = process.inputStream.bufferedReader().readText()
                        val exited = process.waitFor(60, TimeUnit.SECONDS)
                        if (!exited) process.destroyForcibly()
                        if (!exited || process.exitValue() != 0 || !sceneFile.exists()) {
                            cacheDir.deleteRecursively()
                            return@get call.respond(
                                HttpStatusCode.InternalServerError, output.takeLast(2000))
                        }
                    }

                    call.respondText(sceneFile.readText(), ContentType.Application.Json)
                }

            delete(
                "/api/game-assets/blend-cache/{path...}",
                {
                    description =
                        "Clears the cached Blender conversion (scene tree + all OBJ/bbmodel " +
                            "exports) for a .blend file"
                    request {
                        pathParameter<String>("path") {
                            description = "Relative path to a .blend asset"
                        }
                    }
                    response {
                        code(HttpStatusCode.NoContent) {}
                        code(HttpStatusCode.NotFound) { description = "Asset not found" }
                    }
                }) {
                    val relPath =
                        call.parameters.getAll("path")?.joinToString("/")
                            ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val file = File("resources/game-assets/$relPath")
                    if (!file.exists() ||
                        !file.canonicalPath.startsWith(root.canonicalPath) ||
                        file.extension.lowercase() != "blend") {
                        return@delete call.respond(HttpStatusCode.NotFound)
                    }

                    val blendHash = sha256(relPath)
                    File(root, ".cache/blend-scene/$blendHash").deleteRecursively()
                    File(root, ".cache/blend-obj/$blendHash").deleteRecursively()
                    call.respond(HttpStatusCode.NoContent)
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
                        queryParameter<String>("objects") {
                            description =
                                "Comma-separated object names to export; omit to export the whole scene"
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
                    val selectedObjects =
                        call.request.queryParameters["objects"]
                            ?.split(",")
                            ?.map { it.trim() }
                            ?.filter { it.isNotEmpty() }
                            ?.sorted() ?: emptyList()

                    val blendHash = sha256(relPath)
                    val selectionHash = sha256(selectedObjects.joinToString(","))
                    val cacheDir = File(root, ".cache/blend-obj/$blendHash/$selectionHash")
                    val objFile = File(cacheDir, "model.obj")

                    if (!objFile.exists()) {
                        cacheDir.mkdirs()
                        val scriptFile = File(cacheDir, "convert.py")
                        scriptFile.writeText(
                            """
                            import bpy, os, sys
                            out = sys.argv[sys.argv.index("--") + 1]
                            selected_names = sys.argv[sys.argv.index("--") + 2:]
                            out_dir = os.path.dirname(out)
                            # Save every image (packed or external) as a plain file next to the obj,
                            # under the name path_mode='STRIP' will reference in the mtl, so the
                            # texture is always resolvable regardless of how the .blend stored it.
                            for img in bpy.data.images:
                                if img.name == 'Render Result' or img.name == 'Viewer Node':
                                    continue
                                name = bpy.path.basename(img.filepath) if img.filepath else ''
                                if not name:
                                    ext = {'JPEG': 'jpg'}.get(img.file_format, img.file_format.lower())
                                    name = img.name if '.' in img.name else img.name + '.' + ext
                                try:
                                    img.filepath_raw = os.path.join(out_dir, name)
                                    img.save()
                                except Exception:
                                    pass

                            if selected_names:
                                bpy.ops.object.select_all(action='DESELECT')
                                for name in selected_names:
                                    obj = bpy.data.objects.get(name)
                                    if obj:
                                        obj.select_set(True)

                            try:
                                kwargs = dict(filepath=out, export_materials=True, path_mode='STRIP')
                                if selected_names:
                                    kwargs['export_selected_objects'] = True
                                bpy.ops.wm.obj_export(**kwargs)
                            except AttributeError:
                                kwargs = dict(filepath=out, use_materials=True, path_mode='STRIP')
                                if selected_names:
                                    kwargs['use_selection'] = True
                                bpy.ops.export_scene.obj(**kwargs)
                            """
                                .trimIndent())

                        val process =
                            ProcessBuilder(
                                    listOf(
                                        "blender",
                                        "-b",
                                        file.absolutePath,
                                        "--python",
                                        scriptFile.absolutePath,
                                        "--",
                                        objFile.absolutePath) + selectedObjects)
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

            get(
                "/api/game-assets/bbmodel-export/{path...}",
                {
                    description =
                        "Converts an OBJ/MTL mesh into a Blockbench-compatible mesh .bbmodel " +
                            "(cached). The generated mesh elements are not rendered by the admin " +
                            "viewer — open the result in Blockbench to edit it."
                    request {
                        pathParameter<String>("path") {
                            description = "Relative path to a .obj asset"
                        }
                    }
                    response {
                        code(HttpStatusCode.OK) { body<BbmodelExportResponse>() }
                        code(HttpStatusCode.NotFound) { description = "Asset not found" }
                    }
                }) {
                    val relPath =
                        call.parameters.getAll("path")?.joinToString("/")
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val objFile = File("resources/game-assets/$relPath")
                    if (!objFile.exists() ||
                        !objFile.canonicalPath.startsWith(root.canonicalPath) ||
                        objFile.extension.lowercase() != "obj") {
                        return@get call.respond(HttpStatusCode.NotFound)
                    }

                    val bbmodelFile =
                        File(objFile.parentFile, "${objFile.nameWithoutExtension}.bbmodel")
                    if (!bbmodelFile.exists()) {
                        val mtlFile =
                            File(objFile.parentFile, "${objFile.nameWithoutExtension}.mtl")
                        val bbmodel =
                            ObjToBbmodel.convert(
                                objName = objFile.nameWithoutExtension,
                                objContent = objFile.readText(),
                                mtlContent = mtlFile.takeIf { it.exists() }?.readText(),
                                resolveTexture = { textureName ->
                                    val textureFile = File(objFile.parentFile, textureName)
                                    if (!textureFile.exists()) return@convert null
                                    val mime =
                                        contentTypeByExt[textureFile.extension.lowercase()]
                                            ?.toString() ?: "image/png"
                                    ObjToBbmodel.ResolvedTexture(
                                        textureFile.name, mime, textureFile.readBytes())
                                })
                        bbmodelFile.writeText(bbmodel)
                    }

                    val bbmodelRelPath = bbmodelFile.relativeTo(root).path.replace("\\", "/")
                    call.respondText(
                        """{"path":${bbmodelRelPath.toJson()}}""", ContentType.Application.Json)
                }
        }

    private fun String.toJson() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private val String.extension
        get() = substringAfterLast('.', "")

    private fun sha256(input: String) =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
}
