package org.micoli.micraft.macro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacroExecutorTest {
    private val executor = MacroExecutor()

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
}
