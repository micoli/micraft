package org.micoli.micraft.tools

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import org.micoli.micraft.config.validateYamlErrors
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaOpaque
import org.micoli.micraft.schema.JsonSchemaOpen

private enum class Color {
    RED,
    GREEN,
}

@Serializable private data class Nested(val label: String)

@Serializable
private data class Sample(
    val name: String,
    val color: Color,
    val nickname: String? = null,
    val tags: List<String> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val nested: Nested,
    @JsonSchemaConstraint(minimum = 1.0, maximum = 180.0) val fov: Int = 90,
    @JsonSchemaConstraint(minItems = 1, itemPattern = "^[A-Z]+$")
    val codes: List<String> = emptyList(),
)

@Serializable @JsonSchemaOpen private data class OpenSample(val known: String)

@Serializable
@JsonSchemaOpaque
private sealed class Opaque {
    @Serializable @SerialName("A") data class A(val x: Int) : Opaque()
}

@Serializable private data class HoldsOpaque(val slot: Opaque?, val slots: List<Opaque?>)

private inline fun <reified T> descriptor(): SerialDescriptor = serializer<T>().descriptor

class JsonSchemaGeneratorTest {

    @Test
    fun `object schema lists required fields excluding ones with defaults`() {
        val schema = schemaForClass(descriptor<Sample>())
        val required = schema["required"]!!.let { Json.decodeFromJsonElement<List<String>>(it) }
        assertEquals(setOf("name", "color", "nested"), required.toSet())
    }

    @Test
    fun `enum becomes string with enum values`() {
        val schema = schemaForClass(descriptor<Sample>())
        val colorSchema = schema["properties"]!!.asObject()["color"]!!.asObject()
        assertEquals("string", colorSchema["type"]!!.asString())
        val values = colorSchema["enum"]!!.let { Json.decodeFromJsonElement<List<String>>(it) }
        assertEquals(setOf("RED", "GREEN"), values.toSet())
    }

    @Test
    fun `nullable primitive becomes type array with null`() {
        val schema = schemaForClass(descriptor<Sample>())
        val nickname = schema["properties"]!!.asObject()["nickname"]!!.asObject()
        val types = nickname["type"]!!.let { Json.decodeFromJsonElement<List<String>>(it) }
        assertEquals(setOf("string", "null"), types.toSet())
    }

    @Test
    fun `list becomes array of items`() {
        val schema = schemaForClass(descriptor<Sample>())
        val tags = schema["properties"]!!.asObject()["tags"]!!.asObject()
        assertEquals("array", tags["type"]!!.asString())
        assertEquals("string", tags["items"]!!.asObject()["type"]!!.asString())
    }

    @Test
    fun `map becomes object with additionalProperties`() {
        val schema = schemaForClass(descriptor<Sample>())
        val counts = schema["properties"]!!.asObject()["counts"]!!.asObject()
        assertEquals("object", counts["type"]!!.asString())
        assertEquals("integer", counts["additionalProperties"]!!.asObject()["type"]!!.asString())
    }

    @Test
    fun `nested class recurses`() {
        val schema = schemaForClass(descriptor<Sample>())
        val nested = schema["properties"]!!.asObject()["nested"]!!.asObject()
        assertEquals("object", nested["type"]!!.asString())
        assertTrue(nested["properties"]!!.asObject().containsKey("label"))
    }

    @Test
    fun `JsonSchemaConstraint adds keywords without replacing the derived type`() {
        val schema = schemaForClass(descriptor<Sample>())
        val fov = schema["properties"]!!.asObject()["fov"]!!.asObject()
        assertEquals("integer", fov["type"]!!.asString())
        assertEquals(1, fov["minimum"]!!.let { Json.decodeFromJsonElement<Int>(it) })
        assertEquals(180, fov["maximum"]!!.let { Json.decodeFromJsonElement<Int>(it) })
    }

    @Test
    fun `JsonSchemaConstraint item fields target the array's items sub-schema`() {
        val schema = schemaForClass(descriptor<Sample>())
        val codes = schema["properties"]!!.asObject()["codes"]!!.asObject()
        assertEquals(1, codes["minItems"]!!.let { Json.decodeFromJsonElement<Int>(it) })
        val items = codes["items"]!!.asObject()
        assertEquals("string", items["type"]!!.asString())
        assertEquals("^[A-Z]+$", items["pattern"]!!.asString())
    }

    @Test
    fun `JsonSchemaOpaque replaces the real shape with a loose discriminator object`() {
        val schema = schemaForClass(descriptor<HoldsOpaque>())
        val slot = schema["properties"]!!.asObject()["slot"]!!
        // nullable: wrapped in oneOf[null, opaque]
        val oneOf =
            slot.asObject()["oneOf"]!!.let { Json.decodeFromJsonElement<List<JsonElement>>(it) }
        val opaque = oneOf[1].asObject()
        assertEquals("object", opaque["type"]!!.asString())
        assertEquals(
            true, opaque["additionalProperties"]!!.let { Json.decodeFromJsonElement<Boolean>(it) })

        val slots = schema["properties"]!!.asObject()["slots"]!!.asObject()
        assertEquals("array", slots["type"]!!.asString())
    }

    @Test
    fun `JsonSchemaOpen allows additionalProperties`() {
        val schema = schemaForClass(descriptor<OpenSample>())
        assertEquals(
            true, schema["additionalProperties"]!!.let { Json.decodeFromJsonElement<Boolean>(it) })
    }

    @Test
    fun `additionalProperties is false by default`() {
        val schema = schemaForClass(descriptor<Sample>())
        assertEquals(
            false, schema["additionalProperties"]!!.let { Json.decodeFromJsonElement<Boolean>(it) })
    }

    @Test
    fun `generated blocks schema validates a full BlockYamlEntry-shaped file`() {
        val yaml =
            """
            hardness: 2.0
            solid: true
            transparent: false
            minimapColor: [128, 128, 128]
            drops:
              - item: FLINT
                dropRate: 50
                minCount: 1
                maxCount: 2
            """
                .trimIndent()
        assertEquals(emptyList(), validateAgainstBlocksSchema(yaml))
    }

    @Test
    fun `generated blocks schema validates a sparse BlockYamlOverride-shaped file`() {
        val yaml =
            """
            hardness: 3.0
            solid: true
            """
                .trimIndent()
        assertEquals(emptyList(), validateAgainstBlocksSchema(yaml))
    }

    private fun validateAgainstBlocksSchema(yaml: String): List<String> {
        val tempFile = kotlin.io.path.createTempFile(suffix = ".yaml")
        tempFile.writeText(yaml)
        val schemaUri: URI =
            requireNotNull(object {}::class.java.getResource("/schemas/blocks.schema.json")).toURI()
        return validateYamlErrors(tempFile, schemaUri)
    }

    @Test
    fun `generated player schema validates an existing real player save`() {
        val schemaUri: URI =
            requireNotNull(object {}::class.java.getResource("/schemas/player.schema.json")).toURI()
        val save = Path.of("data/world/test_world/players/TestHero.yaml")
        assertEquals(emptyList(), validateYamlErrors(save, schemaUri))
    }
}

private fun JsonElement.asObject() = this as kotlinx.serialization.json.JsonObject

private fun JsonElement.asString() = (this as kotlinx.serialization.json.JsonPrimitive).content
