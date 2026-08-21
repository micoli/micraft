package org.micoli.micraft.tools

import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.serialization.json.*

/**
 * Ports Blockbench's OBJ importer (`js/modeling/mesh/import_obj.ts`) to a standalone converter:
 * parses Wavefront OBJ/MTL text into a `.bbmodel` mesh-element JSON, byte-for-byte compatible with
 * what Blockbench itself writes for a mesh project.
 *
 * The in-repo Babylon viewer only understands cube elements (see `BbModelElement` in
 * `global.d.ts`), so a mesh-type bbmodel produced here won't render in the admin viewer — it is
 * meant to be opened in the Blockbench app for further editing (e.g. re-boxing into cubes).
 */
object ObjToBbmodel {

    /** A texture referenced by the material file, resolved to bytes by the caller. */
    data class ResolvedTexture(val name: String, val mimeType: String, val bytes: ByteArray)

    private val JSON = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private data class MeshVertex(val uuid: String, val position: FloatArray)

    private class MeshBuilder(val name: String) {
        val uuid: String = UUID.randomUUID().toString()
        val vertices = LinkedHashMap<String, FloatArray>()
        val faces = LinkedHashMap<String, JsonObject>()
        val vertexKeyByGlobalIndex = HashMap<Int, String>()
    }

    /**
     * @param objContent raw .obj text
     * @param mtlContent raw .mtl text, if a sibling material file exists
     * @param resolveTexture given a texture filename referenced by `map_Kd`, returns its bytes (or
     *   null if unavailable)
     * @param scale OBJ units → bbmodel units (Blockbench's importer defaults this to 16)
     */
    fun convert(
        objName: String,
        objContent: String,
        mtlContent: String?,
        resolveTexture: (String) -> ResolvedTexture?,
        scale: Float = 16f,
    ): String {
        val materialTextureName = parseMtl(mtlContent)
        val textureIndexByName = LinkedHashMap<String, Int>()
        val textureEntries = mutableListOf<JsonObject>()
        var resolutionWidth = 16
        var resolutionHeight = 16

        fun textureIndexFor(materialName: String?): JsonElement {
            val texName = materialName?.let { materialTextureName[it] } ?: return JsonNull
            val existing = textureIndexByName[texName]
            if (existing != null) return JsonPrimitive(existing)

            val resolved = resolveTexture(texName) ?: return JsonNull
            val index = textureEntries.size
            textureIndexByName[texName] = index
            if (index == 0) {
                ImageIO.read(resolved.bytes.inputStream())?.let { img ->
                    resolutionWidth = img.width
                    resolutionHeight = img.height
                }
            }
            val dataUri =
                "data:${resolved.mimeType};base64,${Base64.getEncoder().encodeToString(resolved.bytes)}"
            textureEntries.add(
                buildJsonObject {
                    put("path", "")
                    put("name", resolved.name)
                    put("folder", "")
                    put("namespace", "")
                    put("id", index.toString())
                    put("particle", false)
                    put("layers_enabled", false)
                    put("visible", true)
                    put("mode", "bitmap")
                    put("saved", true)
                    put("uuid", UUID.randomUUID().toString())
                    put("source", dataUri)
                })
            return JsonPrimitive(index)
        }

        val vertices = mutableListOf<FloatArray>()
        val texCoords = mutableListOf<FloatArray>()
        val meshes = mutableListOf<MeshBuilder>()
        var currentMesh: MeshBuilder? = null
        var currentTexture: JsonElement = JsonNull

        for (rawLine in objContent.split(Regex("[\r\n]+"))) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val args = line.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
            val cmd = args.removeAt(0)

            when (cmd) {
                "o",
                "g" -> {
                    currentMesh =
                        MeshBuilder(args.getOrElse(0) { "unknown" }).also { meshes.add(it) }
                }
                "v" -> {
                    if (currentMesh == null) {
                        currentMesh = MeshBuilder("unknown").also { meshes.add(it) }
                    }
                    vertices.add(
                        floatArrayOf(
                            args[0].toFloat() * scale,
                            args[1].toFloat() * scale,
                            args[2].toFloat() * scale,
                        ))
                }
                "vt" -> texCoords.add(floatArrayOf(args[0].toFloat(), args[1].toFloat()))
                "usemtl" -> currentTexture = textureIndexFor(args.getOrNull(0))
                "f" -> {
                    val mesh = currentMesh ?: continue
                    val corners = args.take(4)
                    val faceVertexUuids = mutableListOf<String>()
                    val faceUv = mutableMapOf<String, JsonElement>()

                    corners.forEach { triplet ->
                        val parts = triplet.split("/")
                        val vIdx = parts[0].toInt()
                        val vtIdx = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toInt()
                        val globalIdx = vIdx - 1

                        val vertexUuid =
                            mesh.vertexKeyByGlobalIndex.getOrPut(globalIdx) {
                                val uuid = UUID.randomUUID().toString()
                                mesh.vertices[uuid] = vertices[globalIdx]
                                uuid
                            }
                        faceVertexUuids.add(vertexUuid)

                        val uv =
                            vtIdx
                                ?.let { texCoords.getOrNull(it - 1) }
                                ?.let {
                                    buildJsonArray {
                                        add(it[0] * resolutionWidth)
                                        add((1f - it[1]) * resolutionWidth)
                                    }
                                }
                                ?: buildJsonArray {
                                    add(0)
                                    add(0)
                                }
                        faceUv[vertexUuid] = uv
                    }

                    val faceUuid = UUID.randomUUID().toString()
                    mesh.faces[faceUuid] = buildJsonObject {
                        put("uv", buildJsonObject { faceUv.forEach { (k, v) -> put(k, v) } })
                        put("vertices", buildJsonArray { faceVertexUuids.forEach { add(it) } })
                        put("texture", currentTexture)
                    }
                }
            }
        }

        val elements =
            meshes
                .filter { it.vertices.isNotEmpty() }
                .map { mesh ->
                    buildJsonObject {
                        put("name", mesh.name)
                        put("type", "mesh")
                        put("uuid", mesh.uuid)
                        put(
                            "origin",
                            buildJsonArray {
                                add(0)
                                add(0)
                                add(0)
                            })
                        put("color", 0)
                        put(
                            "vertices",
                            buildJsonObject {
                                mesh.vertices.forEach { (uuid, pos) ->
                                    put(
                                        uuid,
                                        buildJsonArray {
                                            add(pos[0])
                                            add(pos[1])
                                            add(pos[2])
                                        })
                                }
                            })
                        put("faces", buildJsonObject { mesh.faces.forEach { (k, v) -> put(k, v) } })
                    }
                }

        val root = buildJsonObject {
            put(
                "meta",
                buildJsonObject {
                    put("format_version", "4.10")
                    put("model_format", "free")
                    put("box_uv", false)
                })
            put("name", objName)
            put(
                "resolution",
                buildJsonObject {
                    put("width", resolutionWidth)
                    put("height", resolutionHeight)
                })
            put("elements", JsonArray(elements))
            put("outliner", buildJsonArray { meshes.forEach { add(it.uuid) } })
            put("textures", JsonArray(textureEntries))
        }

        return JSON.encodeToString(root)
    }

    /** `newmtl <name>` … `map_Kd <texture>` → material name to texture filename. */
    private fun parseMtl(mtlContent: String?): Map<String, String> {
        if (mtlContent == null) return emptyMap()
        val result = LinkedHashMap<String, String>()
        var current: String? = null
        for (rawLine in mtlContent.split(Regex("[\r\n]+"))) {
            val line = rawLine.trim()
            when {
                line.startsWith("newmtl") -> current = line.removePrefix("newmtl").trim()
                // Filenames may contain spaces, so keep the rest of the line intact rather than
                // splitting on whitespace (which would drop everything but the last word).
                line.startsWith("map_Kd") ->
                    current?.let { result[it] = line.removePrefix("map_Kd").trim() }
            }
        }
        return result
    }
}
