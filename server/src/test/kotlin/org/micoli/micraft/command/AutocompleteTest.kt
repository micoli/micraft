package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.plugins.goto.GotoCommand
import org.micoli.micraft.plugins.kick.KickCommand
import org.micoli.micraft.plugins.summon.SummonCommand
import org.micoli.micraft.plugins.teleport.TeleportCommand
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class AutocompleteTest {

    @Test
    fun giveCommand_autocompleteArgs_containsArg0() {
        assertTrue(GiveCommand().autocompleteArgs.contains(0))
    }

    @Test
    fun giveCommand_completeArg_filtersOptions() = runBlocking {
        val cmd = GiveCommand()
        val result = cmd.completeArg(0, "c", null, testContext())
        assertTrue(result.contains("cobblestone"))
        assertFalse(result.any { it.startsWith("d") })
    }

    @Test
    fun shadersCommand_completeArg_returnsOnOff() = runBlocking {
        val cmd = ShadersCommand()
        val all = cmd.completeArg(0, "", null, testContext())
        assertEquals(listOf("on", "off"), all)
        val filtered = cmd.completeArg(0, "on", null, testContext())
        assertEquals(listOf("on"), filtered)
    }

    @Test
    fun timeCommand_completeArg_returns24Hours() = runBlocking {
        val cmd = TimeCommand()
        val all = cmd.completeArg(0, "", null, testContext())
        assertEquals(24, all.size)
        val filtered = cmd.completeArg(0, "1", null, testContext())
        assertTrue(
            filtered.containsAll(
                listOf("1", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19")))
    }

    @Test
    fun kickCommand_completeArg_returnsConnectedPlayers() = runBlocking {
        val alice = testSession(name = "Alice")
        val bob = testSession(name = "Bob")
        val cmd = KickCommand()
        val result = cmd.completeArg(0, "A", null, testContext(sessions = listOf(alice, bob)))
        assertEquals(listOf("Alice"), result)
    }

    @Test
    fun teleportCommand_completeArg_returnsConnectedPlayers() = runBlocking {
        val alice = testSession(name = "Alice")
        val cmd = TeleportCommand()
        val result = cmd.completeArg(0, "", null, testContext(sessions = listOf(alice)))
        assertEquals(listOf("Alice"), result)
    }

    @Test
    fun summonCommand_completeArg_returnsConnectedPlayers() = runBlocking {
        val alice = testSession(name = "Alice")
        val cmd = SummonCommand()
        val result = cmd.completeArg(0, "", null, testContext(sessions = listOf(alice)))
        assertEquals(listOf("Alice"), result)
    }

    @Test
    fun talkCommand_completeArg_returnsConnectedPlayers() = runBlocking {
        val alice = testSession(name = "Alice")
        val cmd = TalkCommand()
        val result = cmd.completeArg(0, "", null, testContext(sessions = listOf(alice)))
        assertEquals(listOf("Alice"), result)
    }

    @Test
    fun commandsWithNoOptions_haveEmptyAutocompleteArgs() {
        assertFalse(SaveCommand().autocompleteArgs.contains(0))
        assertFalse(DisconnectCommand().autocompleteArgs.contains(0))
    }

    @Test
    fun gotoCommand_autocompleteArgs_containsArg0() {
        assertTrue(GotoCommand().autocompleteArgs.contains(0))
    }
}
