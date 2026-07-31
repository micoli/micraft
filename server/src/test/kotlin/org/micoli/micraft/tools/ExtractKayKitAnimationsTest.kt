package org.micoli.micraft.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ExtractKayKitAnimationsTest {

    private fun bbmodelRoot(groupScope: Int, animations: JsonArray = JsonArray(emptyList())) =
        buildJsonObject {
            put("meta", buildJsonObject { put("format_version", "5.0") })
            put("multi_file_ruleset", "bedrock_attachable")
            put(
                "groups",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("name", "root")
                            put("uuid", "da541ef9-8630-9ad8-3679-0e7f2a04101f")
                            put("scope", groupScope)
                        })
                })
            put("animations", animations)
        }

    private fun animation(name: String, uuid: String, length: Float = 1f) = buildJsonObject {
        put("uuid", uuid)
        put("name", name)
        put("length", length)
    }

    private fun JsonArray.byName(name: String): JsonObject =
        single { it.jsonObject["name"]!!.jsonPrimitive.content == name }.jsonObject

    @Test
    fun `injects the groups scope into every generated animation`() {
        val merged =
            mergeAnimations(
                bbmodelRoot(groupScope = 1),
                buildJsonArray { add(animation("animation.default_player.Cheering", "gen-uuid")) })

        assertEquals(1, merged.size)
        assertEquals(
            1, merged.byName("animation.default_player.Cheering")["scope"]!!.jsonPrimitive.int)
    }

    @Test
    fun `mirrors a zero group scope instead of hardcoding one`() {
        val merged =
            mergeAnimations(
                bbmodelRoot(groupScope = 0),
                buildJsonArray { add(animation("animation.default_player.Cheering", "gen-uuid")) })

        assertEquals(
            0, merged.byName("animation.default_player.Cheering")["scope"]!!.jsonPrimitive.int)
    }

    @Test
    fun `replaces an animation of the same name in place and keeps its uuid`() {
        val root =
            bbmodelRoot(
                groupScope = 1,
                animations =
                    buildJsonArray {
                        add(animation("animation.default_player.walk", "kept-uuid", length = 3f))
                    })

        val merged =
            mergeAnimations(
                root,
                buildJsonArray {
                    add(animation("animation.default_player.walk", "fresh-uuid", length = 7f))
                })

        assertEquals(1, merged.size)
        val walk = merged.byName("animation.default_player.walk")
        assertEquals("kept-uuid", walk["uuid"]!!.jsonPrimitive.content)
        assertEquals(7f, walk["length"]!!.jsonPrimitive.content.toFloat())
        assertEquals(1, walk["scope"]!!.jsonPrimitive.int)
    }

    @Test
    fun `keeps hand-authored animations the extractor does not know about`() {
        val root =
            bbmodelRoot(
                groupScope = 1,
                animations =
                    buildJsonArray {
                        add(animation("animation.default_player.walk", "walk-uuid"))
                        add(animation("animation.default_player.break", "break-uuid"))
                    })

        val merged =
            mergeAnimations(
                root,
                buildJsonArray { add(animation("animation.default_player.Cheering", "gen-uuid")) })

        assertEquals(3, merged.size)
        // Existing entries stay at their original index, new ones are appended.
        assertEquals(
            listOf(
                "animation.default_player.walk",
                "animation.default_player.break",
                "animation.default_player.Cheering"),
            merged.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        assertEquals(
            "break-uuid",
            merged.byName("animation.default_player.break")["uuid"]!!.jsonPrimitive.content)
        // Untouched animations are not rewritten, so they keep whatever scope they already had.
        assertNull(merged.byName("animation.default_player.walk")["scope"])
    }

    @Test
    fun `merging is idempotent`() {
        val generated = buildJsonArray {
            add(animation("animation.default_player.Cheering", "gen-uuid"))
        }
        val first = mergeAnimations(bbmodelRoot(groupScope = 1), generated)
        val second = mergeAnimations(bbmodelRoot(groupScope = 1, animations = first), generated)

        assertEquals(first, second)
    }

    @Test
    fun `bbmodelGroupScope ignores leading zero scopes`() {
        val root = buildJsonObject {
            put(
                "groups",
                buildJsonArray {
                    add(buildJsonObject { put("scope", 0) })
                    add(buildJsonObject { put("scope", 2) })
                })
        }
        assertEquals(2, bbmodelGroupScope(root))
        assertEquals(0, bbmodelGroupScope(buildJsonObject {}))
    }

    @Test
    fun `updateBbmodel preserves the other root fields`() {
        val file = kotlin.io.path.createTempFile(suffix = ".bbmodel").toFile()
        try {
            file.writeText(bbmodelRoot(groupScope = 1).toString())
            updateBbmodel(
                file,
                buildJsonArray { add(animation("animation.default_player.Cheering", "gen-uuid")) })

            val root = Json.parseToJsonElement(file.readText()).jsonObject
            assertEquals("bedrock_attachable", root["multi_file_ruleset"]!!.jsonPrimitive.content)
            assertEquals("5.0", root["meta"]!!.jsonObject["format_version"]!!.jsonPrimitive.content)
            assertEquals(1, root["groups"]!!.jsonArray.size)
            assertTrue(root["animations"]!!.jsonArray.isNotEmpty())
        } finally {
            file.delete()
        }
    }
}
