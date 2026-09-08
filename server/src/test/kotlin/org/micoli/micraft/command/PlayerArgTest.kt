package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class PlayerArgTest {

    private val alice = testSession(id = "id-alice", name = "Alice_One")
    private val bob = testSession(id = "id-bob", name = "Bob")
    private val ctx = testContext(sessions = listOf(alice, bob))

    @Test
    fun resolvesByIdToken() {
        assertEquals(alice, ctx.resolvePlayerSession("@id-alice"))
    }

    @Test
    fun resolvesByExactName() {
        assertEquals(bob, ctx.resolvePlayerSession("Bob"))
    }

    @Test
    fun resolvesByNameIgnoringCase() {
        assertEquals(alice, ctx.resolvePlayerSession("alice_one"))
    }

    @Test
    fun resolvesByNameWithSanitizedSeparator() {
        assertEquals(alice, ctx.resolvePlayerSession("Alice One"))
    }

    @Test
    fun unknownTokenResolvesToNull() {
        assertNull(ctx.resolvePlayerSession("@id-missing"))
        assertNull(ctx.resolvePlayerSession("Nobody"))
    }

    @Test
    fun canonicalNameReturnsLiveDisplayName() {
        assertEquals("Alice_One", ctx.canonicalPlayerName("@id-alice"))
        assertEquals("Alice_One", ctx.canonicalPlayerName("alice_one"))
        assertEquals("Ghost", ctx.canonicalPlayerName("Ghost"))
    }

    @Test
    fun playerCompletionsLabelByNameValueByIdToken() {
        val result = ctx.playerCompletions("Bo")
        assertEquals(listOf(Completion("Bob", "@id-bob")), result)
    }

    @Test
    fun playerCompletionsExcludeSelf() {
        val result = ctx.playerCompletions("", excludeSelf = alice)
        assertEquals(listOf(Completion("Bob", "@id-bob")), result)
    }
}
