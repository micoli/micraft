package org.micoli.micraft.tick

import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.world.DropConfig
import org.micoli.micraft.world.WorldItemManager

class IntentCollectorTest {
    private fun createDropConfig(): DropConfig {
        val tmp = createTempFile(suffix = ".yaml")
        tmp.toFile().deleteOnExit()
        tmp.writeText(
            "STONE:\n  - item: COBBLESTONE\n    dropRate: 100\n    minCount: 1\n    maxCount: 1\n")
        return DropConfig(tmp)
    }

    private fun makeCollector(
        commands: MutableList<String> = mutableListOf(),
    ): IntentCollector {
        val world = testWorld()
        val wim = WorldItemManager(createDropConfig(), {})
        val breaker = BlockBreaker(world, {}, wim)
        val placer = BlockPlacer(world, {}, {})
        return IntentCollector(
            blockBreaker = breaker,
            blockPlacer = placer,
            onCommand = { _, cmd -> commands.add(cmd) },
        )
    }

    @Test
    fun emptyQueue_returnsSessionDefaults() =
        runBlocking<Unit> {
            val collector = makeCollector()
            val session = testSession()
            val tick = collector.collect(session)
            assertEquals(0f, tick.dx)
            assertEquals(0f, tick.dz)
            assertFalse(tick.jumpRequested)
            assertFalse(tick.flyToggleRequested)
        }

    @Test
    fun commandIntent_dispatchedToOnCommand() =
        runBlocking<Unit> {
            val commands = mutableListOf<String>()
            val collector = makeCollector(commands)
            val session = testSession()
            session.intents.send(ClientMessage.Command("/help"))
            collector.collect(session)
            assertEquals(listOf("/help"), commands)
        }

    @Test
    fun multipleIntents_allProcessed() =
        runBlocking<Unit> {
            val commands = mutableListOf<String>()
            val collector = makeCollector(commands)
            val session = testSession()
            session.intents.send(ClientMessage.Command("/foo"))
            session.intents.send(ClientMessage.Command("/bar"))
            collector.collect(session)
            assertEquals(listOf("/foo", "/bar"), commands)
        }

    @Test
    fun moveIntent_collectedIntoTickInput() =
        runBlocking<Unit> {
            val collector = makeCollector()
            val session = testSession()
            session.intents.send(
                ClientMessage.MoveIntent(
                    dx = 1f,
                    dz = 0.5f,
                    dy = 0f,
                    yaw = 0f,
                    pitch = 0f,
                    stance = org.micoli.micraft.player.PlayerStance.STANDING,
                    jump = true,
                    flyToggle = false,
                    speedUp = false,
                    speedDown = false,
                ))
            val tick = collector.collect(session)
            assertEquals(1f, tick.dx)
            assertEquals(0.5f, tick.dz)
            assertTrue(tick.jumpRequested)
        }
}
