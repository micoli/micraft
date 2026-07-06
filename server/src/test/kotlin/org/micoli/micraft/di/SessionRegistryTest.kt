package org.micoli.micraft.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession

class SessionRegistryTest {

    @Test
    fun setAndGet_returnsRegisteredSession() {
        val registry = SessionRegistry()
        val session = testSession(id = "a")
        registry["a"] = session
        assertEquals(session, registry["a"])
        assertEquals(1, registry.size)
    }

    @Test
    fun get_unknownId_returnsNull() {
        val registry = SessionRegistry()
        assertNull(registry["nope"])
    }

    @Test
    fun remove_deletesSessionAndReturnsIt() {
        val registry = SessionRegistry()
        val session = testSession(id = "a")
        registry["a"] = session
        val removed = registry.remove("a")
        assertEquals(session, removed)
        assertEquals(0, registry.size)
        assertNull(registry["a"])
    }

    @Test
    fun remove_unknownId_returnsNull() {
        val registry = SessionRegistry()
        assertNull(registry.remove("nope"))
    }

    @Test
    fun all_returnsEverySessionAdded() {
        val registry = SessionRegistry()
        registry["a"] = testSession(id = "a")
        registry["b"] = testSession(id = "b")
        assertEquals(2, registry.all().size)
    }

    @Test
    fun broadcast_sendsToAllSessions() = runBlocking {
        val registry = SessionRegistry()
        val a = testSession(id = "a")
        val b = testSession(id = "b")
        registry["a"] = a
        registry["b"] = b

        registry.broadcast(ServerMessage.Notification("hi"))

        assertTrue(a.sent.any { it is ServerMessage.Notification })
        assertTrue(b.sent.any { it is ServerMessage.Notification })
    }
}
