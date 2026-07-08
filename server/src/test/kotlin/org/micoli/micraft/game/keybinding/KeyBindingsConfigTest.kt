package org.micoli.micraft.game.keybinding

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyBindingsConfigTest {

    @Test
    fun missingFile_createsWithDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("keybindings.yaml")
        val bindings = loadKeyBindings(path)
        assertTrue(path.toFile().exists(), "keybindings.yaml should be created")
        assertEquals(listOf("KeyW", "ArrowUp"), bindings["forward"])
    }

    @Test
    fun partialFile_writesMissingKeysBack() {
        val dir = createTempDirectory()
        val path = dir.resolve("keybindings.yaml")
        path.writeText("movement:\n  forward: [KeyW, ArrowUp]\n")
        loadKeyBindings(path)
        val written = path.readText()
        assertTrue(
            written.contains("movement:\n  forward: [KeyW, ArrowUp]"), "existing lines untouched")
        assertTrue(written.contains("# backward"), "missing action must be commented in")
        assertTrue(written.contains("# combat:"), "missing section must be commented in")
    }

    @Test
    fun corruptFile_fallsBackToDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("keybindings.yaml")
        path.writeText("this: [is: not: valid yaml: }")
        val bindings = loadKeyBindings(path)
        assertEquals(listOf("KeyW", "ArrowUp"), bindings["forward"])
    }

    @Test
    fun corruptFile_leftUntouched() {
        val dir = createTempDirectory()
        val path = dir.resolve("keybindings.yaml")
        val original = "this: [is: not: valid yaml: }"
        path.writeText(original)
        loadKeyBindings(path)
        assertEquals(original, path.readText(), "Unparseable file must not be rewritten")
    }

    @Test
    fun reload_isIdempotent_doesNotDuplicateComments() {
        val dir = createTempDirectory()
        val path = dir.resolve("keybindings.yaml")
        path.writeText("movement:\n  forward: [KeyW, ArrowUp]\n")
        loadKeyBindings(path)
        val afterFirstLoad = path.readText()
        loadKeyBindings(path)
        val afterSecondLoad = path.readText()
        assertEquals(
            afterFirstLoad, afterSecondLoad, "Reloading must not duplicate default comments")
        assertEquals(1, Regex("backward").findAll(afterSecondLoad).count())
    }
}
