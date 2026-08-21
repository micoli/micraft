package org.micoli.micraft.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ObjToBbmodelTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `converts a quad mesh into a single mesh element`() {
        val obj =
            """
            o Plane
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 1.0 1.0 0.0
            v 0.0 1.0 0.0
            vt 0.0 0.0
            vt 1.0 0.0
            vt 1.0 1.0
            vt 0.0 1.0
            usemtl Material
            f 1/1 2/2 3/3 4/4
            """
                .trimIndent()
        val mtl = "newmtl Material\nmap_Kd tex.png\n"

        val bbmodel =
            ObjToBbmodel.convert(
                objName = "plane",
                objContent = obj,
                mtlContent = mtl,
                resolveTexture = { null },
                scale = 16f)

        val root = json.parseToJsonElement(bbmodel).jsonObject
        val elements = root["elements"]!!.jsonArray
        assertEquals(1, elements.size)

        val mesh = elements[0].jsonObject
        assertEquals("mesh", mesh["type"]!!.jsonPrimitive.content)
        assertEquals("Plane", mesh["name"]!!.jsonPrimitive.content)

        val vertices = mesh["vertices"]!!.jsonObject
        assertEquals(4, vertices.size)
        val scaledComponents =
            vertices.values.flatMap { it.jsonArray.map { c -> c.jsonPrimitive.content.toFloat() } }
        assertTrue(scaledComponents.any { it == 16f })

        val faces = mesh["faces"]!!.jsonObject
        assertEquals(1, faces.size)
        val face = faces.values.first().jsonObject
        assertEquals(4, face["vertices"]!!.jsonArray.size)
        assertEquals(4, face["uv"]!!.jsonObject.size)

        val outliner = root["outliner"]!!.jsonArray
        assertEquals(JsonArray(listOf(mesh["uuid"]!!)), outliner)
    }

    @Test
    fun `resolves and embeds a referenced texture`() {
        val obj =
            """
            o Plane
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 1.0 1.0 0.0
            usemtl Material
            f 1 2 3
            """
                .trimIndent()
        val mtl = "newmtl Material\nmap_Kd tex.png\n"
        val textureBytes = byteArrayOf(1, 2, 3, 4)

        val bbmodel =
            ObjToBbmodel.convert(
                objName = "plane",
                objContent = obj,
                mtlContent = mtl,
                resolveTexture = { name ->
                    assertEquals("tex.png", name)
                    ObjToBbmodel.ResolvedTexture(name, "image/png", textureBytes)
                })

        val root = json.parseToJsonElement(bbmodel).jsonObject
        val textures = root["textures"]!!.jsonArray
        assertEquals(1, textures.size)
        val texture = textures[0].jsonObject
        assertTrue(texture["source"]!!.jsonPrimitive.content.startsWith("data:image/png;base64,"))

        val face =
            (root["elements"]!!.jsonArray[0].jsonObject["faces"]!!.jsonObject)
                .values
                .first()
                .jsonObject
        assertEquals(0, face["texture"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `resolves a texture filename containing spaces`() {
        val obj =
            """
            o Plane
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 1.0 1.0 0.0
            usemtl Material
            f 1 2 3
            """
                .trimIndent()
        val mtl = "newmtl Material\nmap_Kd Fire Axe.png\n"

        val bbmodel =
            ObjToBbmodel.convert(
                objName = "plane",
                objContent = obj,
                mtlContent = mtl,
                resolveTexture = { name ->
                    assertEquals("Fire Axe.png", name)
                    ObjToBbmodel.ResolvedTexture(name, "image/png", byteArrayOf(1))
                })

        val root = json.parseToJsonElement(bbmodel).jsonObject
        assertEquals(1, root["textures"]!!.jsonArray.size)
    }

    @Test
    fun `no material means untextured faces`() {
        val obj =
            """
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 1.0 1.0 0.0
            f 1 2 3
            """
                .trimIndent()

        val bbmodel =
            ObjToBbmodel.convert(
                objName = "loose", objContent = obj, mtlContent = null, resolveTexture = { null })

        val root = json.parseToJsonElement(bbmodel).jsonObject
        val mesh = root["elements"]!!.jsonArray[0].jsonObject
        assertEquals("unknown", mesh["name"]!!.jsonPrimitive.content)
        val face = mesh["faces"]!!.jsonObject.values.first().jsonObject
        assertEquals(JsonNull, face["texture"])
    }
}
