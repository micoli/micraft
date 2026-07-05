package org.micoli.micraft.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatChannelManagerTest {
    private fun mgr() = ChatChannelManager()

    @Test
    fun builtinChannels_exist() {
        val m = mgr()
        assertTrue(m.channelExists("world"))
        assertTrue(m.channelExists("around"))
        assertTrue(m.channelExists("system"))
        assertTrue(m.channelExists("game"))
        assertTrue(m.channelExists("combat"))
    }

    @Test
    fun unknownChannel_doesNotExist() {
        assertFalse(mgr().channelExists("mystery"))
        assertFalse(mgr().channelExists(""))
    }

    @Test
    fun registerChannel_makesItExist() {
        val m = mgr()
        assertFalse(m.channelExists("guild"))
        m.registerChannel("guild")
        assertTrue(m.channelExists("guild"))
    }

    @Test
    fun dmChannel_existsWithDmPrefix() {
        val m = mgr()
        assertTrue(m.channelExists("dm:alice:bob"))
        assertTrue(m.channelExists("dm:anything"))
        assertTrue(m.channelExists("dm:x:y:z"))
    }

    @Test
    fun dmChannelName_sortedAlphabetically() {
        val m = mgr()
        assertEquals("dm:alice:bob", m.dmChannelName("bob", "alice"))
        assertEquals("dm:alice:bob", m.dmChannelName("alice", "bob"))
    }

    @Test
    fun dmChannelName_symmetric() {
        val m = mgr()
        assertEquals(m.dmChannelName("x", "y"), m.dmChannelName("y", "x"))
    }

    @Test
    fun listKnownChannels_isSortedAndIncludesBuiltins() {
        val m = mgr()
        m.registerChannel("zzz-channel")
        m.registerChannel("aaa-channel")
        val list = m.listKnownChannels()
        assertTrue(list.contains("world"))
        assertTrue(list.contains("aaa-channel"))
        assertTrue(list.contains("zzz-channel"))
        assertEquals(list, list.sorted())
    }

    @Test
    fun protectedChannels_areBuiltins() {
        assertTrue(ChatChannelManager.PROTECTED.all { it in ChatChannelManager.BUILTIN })
    }
}
