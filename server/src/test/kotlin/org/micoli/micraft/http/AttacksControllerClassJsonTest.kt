package org.micoli.micraft.http

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.classes.ClassAttackAccess
import org.micoli.micraft.game.classes.ClassLevelEntry

/**
 * The default kotlinx.serialization `Json` omits properties left at their default value, so an
 * attacks-only level (spells = emptyList()) serialized that way drops the "spells" key entirely —
 * the TS client reads `entry.spells` unconditionally and crashes. `/api/classes` must always emit
 * both arrays, matching the encodeDefaults=true Json used by AttacksController.
 */
class AttacksControllerClassJsonTest {
    private val classJson = Json { encodeDefaults = true }

    @Test
    fun `attacks-only level still serializes an empty spells array`() {
        val entry =
            ClassLevelEntry(attacks = listOf(ClassAttackAccess("slash", 1)), spells = emptyList())
        val json = classJson.encodeToString(ClassLevelEntry.serializer(), entry)
        assertTrue(json.contains("\"spells\":[]"), "expected spells key in $json")
    }

    @Test
    fun `spells-only level still serializes an empty attacks array`() {
        val entry = ClassLevelEntry(attacks = emptyList(), spells = listOf("fireball"))
        val json = classJson.encodeToString(ClassLevelEntry.serializer(), entry)
        assertTrue(json.contains("\"attacks\":[]"), "expected attacks key in $json")
    }
}
