package org.micoli.micraft.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.serializer

class YamlPatchWriterTest {

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
}
