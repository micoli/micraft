package org.micoli.micraft.tick

import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.DropConfig
import org.micoli.micraft.world.WorldItemManager

class BlockBreakerTest {

    private fun createDropConfig(): DropConfig {
        val tmp = createTempFile(suffix = ".yaml")
        tmp.toFile().deleteOnExit()
        tmp.writeText(
            "STONE:\n  - item: COBBLESTONE\n    dropRate: 100\n    minCount: 1\n    maxCount: 1\n")
        return DropConfig(tmp)
    }

    private fun noopWim(
        broadcasts: MutableList<ServerMessage> = mutableListOf()
    ): WorldItemManager = WorldItemManager(createDropConfig(), { broadcasts.add(it) })

    @Test
    fun handleStart_validBlock_setsBreakTarget() {
        // Stone at y=5, player at y=6 (within 6 blocks)
        val world = testWorld(Triple(8, 5, 8))
        val wim = noopWim()
        val breaker = BlockBreaker(world, {}, wim)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        val intent = ClientMessage.BlockBreakStart(BlockPos(8, 5, 8))
        breaker.handleStart(session, intent)
        assertNotNull(session.breakTarget)
        assertEquals(BlockPos(8, 5, 8), session.breakTarget)
        assertEquals(0, session.breakProgress)
    }

    @Test
    fun handleStart_airBlock_ignores() {
        val world = testWorld() // no blocks = all AIR
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 5, 8)))
        assertNull(session.breakTarget)
    }

    @Test
    fun handleStart_bedrockBlock_ignores() {
        // Place BEDROCK manually via MapChunkGenerator
        val blocks = mapOf(Triple(8, 5, 8) to BlockType.BEDROCK)
        val world =
            org.micoli.micraft.world.WorldState(
                org.micoli.micraft.support.MapChunkGenerator(blocks))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 5, 8)))
        assertNull(session.breakTarget)
    }

    @Test
    fun handleStart_tooFar_ignores() {
        val world = testWorld(Triple(8, 5, 8))
        val breaker = BlockBreaker(world, {}, noopWim())
        // Player at y=20 → distance to block at y=5 is ~14.5 > 6
        val session = testSession(pos = Vec3(8.5f, 20f, 8.5f))
        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 5, 8)))
        assertNull(session.breakTarget)
    }

    @Test
    fun handleStop_clearsTarget() {
        val world = testWorld(Triple(8, 5, 8))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 5, 8)))
        assertNotNull(session.breakTarget)
        breaker.handleStop(session)
        assertNull(session.breakTarget)
        assertEquals(0, session.breakProgress)
    }

    @Test
    fun tick_noTarget_doesNothing() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val world = testWorld()
        val breaker = BlockBreaker(world, { broadcasts.add(it) }, noopWim(broadcasts))
        val session = testSession()
        breaker.tick(session)
        assertTrue(broadcasts.isEmpty())
        assertTrue(session.sent.isEmpty())
    }

    @Test
    fun tick_incrementsProgress_sendsBlockBreakProgress() = runBlocking {
        // STONE has hardness=5, so first tick should send progress, not break
        val world = testWorld(Triple(8, 5, 8))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.breakTarget = BlockPos(8, 5, 8)
        session.breakProgress = 0
        breaker.tick(session)
        assertEquals(1, session.breakProgress)
        assertTrue(session.sent.any { it is ServerMessage.BlockBreakProgress })
    }

    @Test
    fun tick_completesBreak_setsAirInWorld() = runBlocking {
        // SNOW has hardness=1 → breaks on first tick
        val blocks = mapOf(Triple(8, 5, 8) to BlockType.SNOW)
        val world =
            org.micoli.micraft.world.WorldState(
                org.micoli.micraft.support.MapChunkGenerator(blocks))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.breakTarget = BlockPos(8, 5, 8)
        session.breakProgress = 0
        breaker.tick(session)
        assertEquals(BlockType.AIR, world.getBlock(8, 5, 8))
        assertNull(session.breakTarget)
    }

    @Test
    fun tick_completesBreak_broadcastsWorldUpdate() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val blocks = mapOf(Triple(8, 5, 8) to BlockType.SNOW)
        val world =
            org.micoli.micraft.world.WorldState(
                org.micoli.micraft.support.MapChunkGenerator(blocks))
        val breaker = BlockBreaker(world, { broadcasts.add(it) }, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.breakTarget = BlockPos(8, 5, 8)
        breaker.tick(session)
        assertTrue(broadcasts.any { it is ServerMessage.WorldUpdate })
    }

    @Test
    fun tick_completesBreak_addsToHistory() = runBlocking {
        val blocks = mapOf(Triple(8, 5, 8) to BlockType.SNOW)
        val world =
            org.micoli.micraft.world.WorldState(
                org.micoli.micraft.support.MapChunkGenerator(blocks))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.breakTarget = BlockPos(8, 5, 8)
        breaker.tick(session)
        assertEquals(1, session.actionHistory.size)
        val record = session.actionHistory.last()
        require(record is org.micoli.micraft.session.WorldActionRecord.Break)
        assertEquals(BlockType.SNOW, record.blockType)
    }

    @Test
    fun tick_historyOverMax_removesOldest() = runBlocking {
        // Break 21 snow blocks (hardness=1) to exceed MAX_UNDO_HISTORY=20
        var xOff = 0
        val blocks = (0..20).associate { Triple(8 + it, 5, 8) to BlockType.SNOW }
        val world =
            org.micoli.micraft.world.WorldState(
                org.micoli.micraft.support.MapChunkGenerator(blocks))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        for (x in 8..28) {
            session.breakTarget = BlockPos(x, 5, 8)
            session.breakProgress = 0
            breaker.tick(session)
        }
        assertTrue(session.actionHistory.size <= 20)
    }
}
