package org.micoli.micraft.config

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

class YamlPatchWriterTest {

    @Serializable private data class Entry(val label: String = "", val amount: Int = 1)

    @Test
    fun allKeysPresent_outputUnchanged() {
        val original = "foo: 1\nbar: 2"
        val section =
            YamlSection(
                key = "",
                fields =
                    listOf(
                        YamlField("foo", 1, Int.serializer(), present = true),
                        YamlField("bar", 2, Int.serializer(), present = true),
                    ),
            )
        val result = spliceMissingAsComments(original, section)
        assertEquals(original, result)
    }

    @Test
    fun missingScalarKey_appendedAsComment() {
        val original = "foo: 1"
        val section =
            YamlSection(
                key = "",
                fields =
                    listOf(
                        YamlField("foo", 1, Int.serializer(), present = true),
                        YamlField("bar", 2, Int.serializer(), present = false),
                    ),
            )
        val result = spliceMissingAsComments(original, section)
        val lines = result.lines()
        assertEquals("foo: 1", lines[0])
        assertEquals("# bar: 2", lines[1])
    }

    @Test
    fun missingWholeSection_appendedFullyCommented() {
        val original = "world:\n  chunkSize: 16\n"
        val section =
            YamlSection(
                key = "",
                subsections =
                    listOf(
                        YamlSection(
                            key = "world",
                            present = true,
                            fields = listOf(YamlField("chunkSize", 16, Int.serializer(), true)),
                        ),
                        YamlSection(
                            key = "chunks",
                            present = false,
                            fields = listOf(YamlField("httpWorkers", 4, Int.serializer(), false)),
                        ),
                    ),
            )
        val result = spliceMissingAsComments(original, section)
        assertTrue(result.contains("world:\n  chunkSize: 16"), "existing section untouched")
        assertTrue(result.contains("# chunks:"))
        assertTrue(result.contains("# httpWorkers: 4"))
    }

    @Test
    fun emptyOriginalText_fullyCommentedOutput() {
        val section =
            YamlSection(
                key = "",
                fields = listOf(YamlField("foo", 1, Int.serializer(), present = false)),
            )
        val result = spliceMissingAsComments("", section)
        assertEquals("# foo: 1", result)
    }

    @Test
    fun missingSubKeyInsertedInsideExistingSection() {
        val original = "world:\n  chunkSize: 16"
        val section =
            YamlSection(
                key = "",
                subsections =
                    listOf(
                        YamlSection(
                            key = "world",
                            present = true,
                            fields =
                                listOf(
                                    YamlField("chunkSize", 16, Int.serializer(), true),
                                    YamlField("waterLevel", 65, Int.serializer(), false),
                                ),
                        )),
            )
        val result = spliceMissingAsComments(original, section)
        val lines = result.lines()
        assertEquals("world:", lines[0])
        assertEquals("  chunkSize: 16", lines[1])
        assertEquals("  # waterLevel: 65", lines[2])
    }

    @Test
    fun yamlMapSection_missingEntry_appendedFullyCommented() {
        val original = "COBBLESTONE:\n  label: COB\n  amount: 4\n"
        val node = Yaml.default.parseToYamlNode(original)
        val entries = mapOf("COBBLESTONE" to Entry("COB", 4), "DIRT" to Entry("DRT", 1))
        val result = spliceMissingAsComments(original, yamlMapSection(entries, node))
        assertTrue(result.contains("label: COB"), "existing entry untouched")
        assertTrue(!result.contains("# label: \"COB\""), "existing entry not commented")
        assertTrue(result.contains("# DIRT:"))
        assertTrue(result.contains("# label: \"DRT\""))
    }

    @Test
    fun yamlMapSection_missingFieldInExistingEntry_appendedAsComment() {
        val original = "COBBLESTONE:\n  label: COB\n"
        val node = Yaml.default.parseToYamlNode(original)
        val entries = mapOf("COBBLESTONE" to Entry("COB", 4))
        val result = spliceMissingAsComments(original, yamlMapSection(entries, node))
        assertTrue(result.contains("COBBLESTONE:\n  label: COB"), "existing lines untouched")
        assertTrue(result.contains("  # amount: 4"))
    }

    @Serializable private data class Nested(val inner: Int = 0)

    @Serializable
    private data class Config(
        val name: String = "",
        val amount: Int = 1,
        val nested: Nested = Nested()
    )

    @Test
    fun mergeConfig_missingField_takesDefaultValue() {
        val original = "name: custom\n"
        val node = Yaml.default.parseToYamlNode(original)
        val decoded = Yaml.default.decodeFromString(Config.serializer(), original)
        val default = Config(name = "default", amount = 42, nested = Nested(7))
        val merged = mergeConfig(Config::class, decoded, default, node)
        assertEquals("custom", merged.name, "present field keeps user value")
        assertEquals(42, merged.amount, "absent field takes default value")
        assertEquals(7, merged.nested.inner, "absent nested field takes default value")
    }

    @Test
    fun mergeConfig_presentNestedField_keepsUserValue() {
        val original = "nested:\n  inner: 99\n"
        val node = Yaml.default.parseToYamlNode(original)
        val decoded = Yaml.default.decodeFromString(Config.serializer(), original)
        val default = Config(name = "default", amount = 42, nested = Nested(7))
        val merged = mergeConfig(Config::class, decoded, default, node)
        assertEquals(99, merged.nested.inner, "present nested field keeps user value")
        assertEquals("default", merged.name, "absent field takes default value")
    }

    @Test
    fun mergeMapConfig_missingEntry_addedFromDefaultAndActive() {
        val decodedMap = mapOf("COBBLESTONE" to Entry("COB", 4))
        val defaultMap = mapOf("COBBLESTONE" to Entry("COB", 4), "DIRT" to Entry("DRT", 1))
        val merged = mergeMapConfig(decodedMap, defaultMap, node = null)
        assertEquals(2, merged.size)
        assertEquals(Entry("DRT", 1), merged["DIRT"], "missing default entry is active in result")
    }

    @Test
    fun mergeMapConfig_missingFieldInExistingEntry_takesDefaultValue() {
        val original = "COBBLESTONE:\n  label: COB\n"
        val node = Yaml.default.parseToYamlNode(original)
        val decodedMap = mapOf("COBBLESTONE" to Entry(label = "COB", amount = 1))
        val defaultMap = mapOf("COBBLESTONE" to Entry(label = "COB", amount = 4))
        val merged = mergeMapConfig(decodedMap, defaultMap, node)
        assertEquals(4, merged.getValue("COBBLESTONE").amount, "absent field takes default value")
    }
}
