package org.micoli.micraft.macro

import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MacroExecutorTest {
    private val executor = MacroExecutor()

    @Test
    fun `MACRO_CONTEXT_SCHEMA covers all MacroContext fields and no more`() {
        val schemaNames = MACRO_CONTEXT_SCHEMA.map { it.name }.toSet()
        val contextFields = MacroContext::class.memberProperties.map { it.name }.toSet()
        // posX/posY/posZ are exposed as position.x/y/z in JEXL
        val posFields = setOf("posX", "posY", "posZ")
        val directFields = contextFields - posFields

        val missing = directFields - schemaNames
        assertTrue(missing.isEmpty(), "MacroContext fields missing from schema: $missing")

        val posEntry = MACRO_CONTEXT_SCHEMA.find { it.name == "position" }
        assertNotNull(posEntry, "'position' entry missing from schema")
        assertEquals(setOf("x", "y", "z"), posEntry.children.toSet(), "position.children mismatch")

        val orphaned = schemaNames - (directFields + setOf("position"))
        assertTrue(orphaned.isEmpty(), "Schema entries with no MacroContext field: $orphaned")
    }

    @Test
    fun `send captures commands`() {
        val sent = mutableListOf<String>()
        executor.execute("send('/heal')", onSend = { sent.add(it) }, onAction = {})
        assertEquals(listOf("/heal"), sent)
    }

    @Test
    fun `multiple send calls captured in order`() {
        val sent = mutableListOf<String>()
        executor.execute(
            "send('/heal'); send('/tp 0 64 0')", onSend = { sent.add(it) }, onAction = {})
        assertEquals(listOf("/heal", "/tp 0 64 0"), sent)
    }

    @Test
    fun `action is callable without error`() {
        val actions = mutableListOf<String>()
        executor.execute("action('jump')", onSend = {}, onAction = { actions.add(it) })
        assertEquals(listOf("jump"), actions)
    }

    @Test
    fun `jexl conditionals work`() {
        val sent = mutableListOf<String>()
        executor.execute("if (1 > 0) { send('/heal') }", onSend = { sent.add(it) }, onAction = {})
        assertEquals(listOf("/heal"), sent)
    }

    @Test
    fun `jexl string concatenation works`() {
        val sent = mutableListOf<String>()
        executor.execute("send('/tp ' + '0 64 0')", onSend = { sent.add(it) }, onAction = {})
        assertEquals(listOf("/tp 0 64 0"), sent)
    }

    @Test
    fun `invalid script throws exception`() {
        val result = runCatching { executor.execute("send(", onSend = {}, onAction = {}) }
        assertTrue(result.isFailure, "Expected exception for invalid JEXL syntax")
    }

    @Test
    fun `sandbox blocks class instantiation`() {
        val result = runCatching {
            executor.execute(
                "new(\"java.lang.ProcessBuilder\", [\"/bin/sh\"])",
                onSend = {},
                onAction = {},
            )
        }
        assertTrue(result.isFailure, "Expected exception when accessing forbidden class")
    }

    @Test
    fun `context position accessible in script`() {
        val sent = mutableListOf<String>()
        val ctx = MacroContext(posX = 42f, posY = 64f, posZ = 100f)
        executor.execute(
            "send('/tp ' + position.x + ' ' + position.y + ' ' + (position.z+10))",
            context = ctx,
            onSend = { sent.add(it) },
            onAction = {},
        )
        assertEquals(listOf("/tp 42.0 64.0 110.0"), sent)
    }

    @Test
    fun `context biome accessible in script`() {
        val sent = mutableListOf<String>()
        val ctx = MacroContext(biome = "forest")
        executor.execute(
            "send(biome)",
            context = ctx,
            onSend = { sent.add(it) },
            onAction = {},
        )
        assertEquals(listOf("forest"), sent)
    }

    @Test
    fun `context currentHp used in conditional`() {
        val sent = mutableListOf<String>()
        val ctx = MacroContext(currentHp = 15)
        executor.execute(
            "if (currentHp < 20) { send('/heal') }",
            context = ctx,
            onSend = { sent.add(it) },
            onAction = {},
        )
        assertEquals(listOf("/heal"), sent)
    }

    @Test
    fun `context effects list accessible`() {
        val sent = mutableListOf<String>()
        val ctx = MacroContext(effects = listOf("Poisoned", "Burning"))
        executor.execute(
            "send(effects[0])",
            context = ctx,
            onSend = { sent.add(it) },
            onAction = {},
        )
        assertEquals(listOf("Poisoned"), sent)
    }

    @Test
    fun `context pitch and yaw accessible`() {
        val sent = mutableListOf<String>()
        val ctx = MacroContext(yaw = 45f, pitch = 30f)
        executor.execute(
            "send(pitch + ':' + yaw)",
            context = ctx,
            onSend = { sent.add(it) },
            onAction = {},
        )
        assertEquals(listOf("30.0:45.0"), sent)
    }

    @Test
    fun `all MACRO_CONTEXT_SCHEMA entries are bound in JEXL context`() {
        val ctx =
            MacroContext(
                posX = 1f,
                posY = 2f,
                posZ = 3f,
                biome = "forest",
                yaw = 90f,
                pitch = 0f,
                currentHp = 100,
                currentMana = 50,
                effects = listOf("Poisoned"),
            )
        for (v in MACRO_CONTEXT_SCHEMA) {
            val result = runCatching {
                executor.execute("${v.name} != null", context = ctx, onSend = {}, onAction = {})
            }
            assertTrue(
                result.isSuccess,
                "Schema var '${v.name}' not accessible: ${result.exceptionOrNull()?.message}")
            for (child in v.children) {
                val childResult = runCatching {
                    executor.execute(
                        "${v.name}.$child != null", context = ctx, onSend = {}, onAction = {})
                }
                assertTrue(
                    childResult.isSuccess,
                    "Schema child '${v.name}.$child' not accessible: ${childResult.exceptionOrNull()?.message}",
                )
            }
        }
    }
}
