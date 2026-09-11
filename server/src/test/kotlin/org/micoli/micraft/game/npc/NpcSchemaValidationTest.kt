package org.micoli.micraft.game.npc

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.config.SchemaValidation

class NpcSchemaValidationTest {

    private fun tmpYaml(content: String) =
        createTempDirectory().resolve("c.yaml").apply { writeText(content) }

    @Test
    fun validNpcYaml_hasNoErrors() {
        val errors =
            SchemaValidation.errors(
                tmpYaml(
                    """
                    behavior: random_movable
                    width: 0.5
                    height: 0.9
                    tier: ELITE
                    aggroMode: AGGRESSIVE
                    """
                        .trimIndent()),
                "npcs.schema.json")
        assertEquals(emptyList(), errors)
    }

    @Test
    fun unknownBehaviorValue_isRejected() {
        val errors =
            SchemaValidation.errors(
                tmpYaml(
                    """
                    behavior: not_a_real_behavior
                    """
                        .trimIndent()),
                "npcs.schema.json")
        assertTrue(errors != null && errors.isNotEmpty(), "expected schema violations, got $errors")
    }

    @Test
    fun tameBaseChance_outOfRange_isRejected() {
        val errors =
            SchemaValidation.errors(
                tmpYaml(
                    """
                    behavior: animal
                    tameable: true
                    tameBaseChance: 2.5
                    """
                        .trimIndent()),
                "npcs.schema.json")
        assertTrue(errors != null && errors.isNotEmpty(), "expected schema violations, got $errors")
    }

    @Test
    fun unknownTierValue_isRejected() {
        val errors =
            SchemaValidation.errors(
                tmpYaml(
                    """
                    behavior: static
                    tier: LEGENDARY
                    """
                        .trimIndent()),
                "npcs.schema.json")
        assertTrue(errors != null && errors.isNotEmpty(), "expected schema violations, got $errors")
    }
}
