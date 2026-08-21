package org.micoli.micraft.game.world.block

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.session.WorldActionRecord
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.MapChunkGenerator
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class BlockBreakerTest {

    private fun createDropConfig(): DropConfig {
        val resourcesDir = createTempDirectory("resources_blocks")
        val blockDir = resourcesDir.resolve("STONE")
        blockDir.toFile().mkdirs()
        blockDir
            .resolve("STONE.yaml")
            .writeText(
                "hardness: 1\nsolid: true\nminimapColor: [0, 0, 0]\ndrops:\n- item: COBBLESTONE\n  dropRate: 100\n  minCount: 1\n  maxCount: 1\n")
        return DropConfig(BlockRegistryLoader(resourcesDir, createTempDirectory("data_blocks")))
    }

    private fun noopWim(
        broadcasts: MutableList<ServerMessage> = mutableListOf()
    ): WorldItemManager = WorldItemManager(createDropConfig(), { broadcasts.add(it) })

    private fun registerLegoPiece() {
        BlockRegistry.load(
            mapOf(
                BlockType.LEGO_PIECE to
                    BlockDefinition(
                        hardness = 1f,
                        solid = true,
                        isCubic = false,
                        replaceable = false,
                        brickSize = listOf(0.5f, 0.666f, 0.5f),
                    )))
    }

    @Test
    fun handleStart_validBlock_setsBreakTarget() {
        val world = testWorld(Triple(8, 5, 8))
        val wim = noopWim()
        val breaker = BlockBreaker(world, {}, wim)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        val intent = ClientMessage.BlockBreakStart(BlockPos(8, 5, 8))
        breaker.handleStart(session, intent)
        assertNotNull(session.breakTarget)
        assertEquals(BlockPos(8, 5, 8), session.breakTarget)
    }

    @Test
    fun handleStart_insideProtectedZone_ignores() {
        val world = testWorld(Triple(8, 5, 8))
        val wim = noopWim()
        val registry = org.micoli.micraft.game.world.instance.InstanceRegistry(null)
        registry.create(
            name = "Arena",
            yMin = 0,
            yMax = 16,
            chunks = setOf(org.micoli.micraft.game.world.ChunkPos(0, 0)),
            ownerName = "Alice",
        )
        val breaker = BlockBreaker(world, {}, wim, instanceRegistry = registry)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 5, 8)))
        assertNull(session.breakTarget)
    }

    @Test
    fun handleStart_airBlock_ignores() {
        val world = testWorld()
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 5, 8)))
        assertNull(session.breakTarget)
    }

    @Test
    fun handleStart_bedrockBlock_ignores() {
        val blocks = mapOf(Triple(8, 5, 8) to BlockType.BEDROCK)
        val world = WorldState(MapChunkGenerator(blocks))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 5, 8)))
        assertNull(session.breakTarget)
    }

    @Test
    fun handleStart_tooFar_ignores() {
        val world = testWorld(Triple(8, 5, 8))
        val breaker = BlockBreaker(world, {}, noopWim())
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
        breaker.tick(session)
        assertTrue(session.sent.any { it is ServerMessage.BlockBreakProgress })
    }

    @Test
    fun tick_completesBreak_setsAirInWorld() = runBlocking {
        // SNOW has hardness=1 → breaks on first tick
        val blocks = mapOf(Triple(8, 5, 8) to BlockType.SNOW)
        val world = WorldState(MapChunkGenerator(blocks))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.breakTarget = BlockPos(8, 5, 8)
        breaker.tick(session)
        assertEquals(BlockType.AIR, world.getBlock(8, 5, 8))
        assertNull(session.breakTarget)
    }

    @Test
    fun tick_completesBreak_broadcastsWorldUpdate() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val blocks = mapOf(Triple(8, 5, 8) to BlockType.SNOW)
        val world = WorldState(MapChunkGenerator(blocks))
        val breaker = BlockBreaker(world, { broadcasts.add(it) }, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.breakTarget = BlockPos(8, 5, 8)
        breaker.tick(session)
        assertTrue(broadcasts.any { it is ServerMessage.WorldUpdate })
    }

    @Test
    fun tick_completesBreak_addsToHistory() = runBlocking {
        val blocks = mapOf(Triple(8, 5, 8) to BlockType.SNOW)
        val world = WorldState(MapChunkGenerator(blocks))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.breakTarget = BlockPos(8, 5, 8)
        breaker.tick(session)
        assertEquals(1, session.actionHistory.size)
        val record = session.actionHistory.last()
        require(record is WorldActionRecord.Break)
        assertEquals(BlockType.SNOW, record.blockType)
    }

    @Test
    fun tick_historyOverMax_removesOldest() = runBlocking {
        // Break 21 snow blocks (hardness=1) to exceed MAX_UNDO_HISTORY=20
        val blocks = (0..20).associate { Triple(8 + it, 5, 8) to BlockType.SNOW }
        val world = WorldState(MapChunkGenerator(blocks))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        for (x in 8..28) {
            session.breakTarget = BlockPos(x, 5, 8)
            breaker.tick(session)
        }
        assertTrue(session.actionHistory.size <= 20)
    }

    @Test
    fun tick_twoSessionsSameBlock_accumulateTicks() = runBlocking {
        // STONE hardness=5; two sessions each tick once → accumulated ticks=2
        val world = testWorld(Triple(8, 5, 8))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session1 = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        val session2 = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session1.breakTarget = BlockPos(8, 5, 8)
        session2.breakTarget = BlockPos(8, 5, 8)
        breaker.tick(session1) // block ticks=1
        breaker.tick(session2) // block ticks=2
        assertTrue(session1.sent.any { it is ServerMessage.BlockBreakProgress })
        assertTrue(session2.sent.any { it is ServerMessage.BlockBreakProgress })
        val progress2 = session2.sent.filterIsInstance<ServerMessage.BlockBreakProgress>().last()
        assertEquals(2, progress2.progress)
    }

    @Test
    fun tick_bufferEviction_oldestEntryRemoved() = runBlocking {
        val world = testWorld(Triple(8, 5, 8), Triple(9, 5, 8), Triple(10, 5, 8))
        val breaker = BlockBreaker(world, {}, noopWim(), bufferSize = 2)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.breakTarget = BlockPos(8, 5, 8)
        breaker.tick(session) // buffer: {(8,5,8)=1}
        session.breakTarget = BlockPos(9, 5, 8)
        breaker.tick(session) // buffer: {(8,5,8)=1, (9,5,8)=1}
        // 3rd block triggers eviction of (8,5,8)
        session.breakTarget = BlockPos(10, 5, 8)
        breaker.tick(session) // buffer: {(9,5,8)=1, (10,5,8)=1}
        // Back to (8,5,8): was evicted, starts from 0 → progress reported as 1
        session.breakTarget = BlockPos(8, 5, 8)
        breaker.tick(session)
        val progressMsg =
            session.sent.filterIsInstance<ServerMessage.BlockBreakProgress>().last {
                it.pos == BlockPos(8, 5, 8)
            }
        assertEquals(1, progressMsg.progress)
    }

    @Test
    fun tick_xzYFractional_removesOnlyTargetedSlot() = runBlocking {
        registerLegoPiece()
        val world = testWorld()
        // Slot (0,0): two stacked pieces. Slot (1,0): one piece.
        BlockPlacer.placeAt(BlockPos(8, 5, 8), BlockType.LEGO_PIECE, 0, 0, 0, 0, world)
        BlockPlacer.placeAt(BlockPos(8, 5, 8), BlockType.LEGO_PIECE, 0, 0, 0, 0, world)
        BlockPlacer.placeAt(BlockPos(8, 5, 8), BlockType.LEGO_PIECE, 0, 0, 1, 0, world)
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.breakTarget = BlockPos(8, 5, 8)
        session.breakTargetXOffset = 1
        session.breakTargetZOffset = 0
        breaker.tick(session)
        assertTrue(world.getFractionalYOffsetsAt(8, 5, 8, 1, 0).isEmpty())
        assertEquals(listOf(0, 1), world.getFractionalYOffsetsAt(8, 5, 8, 0, 0).sorted())
        assertEquals(BlockType.LEGO_PIECE, world.getBlock(8, 5, 8))
    }

    @Test
    fun tick_xzYFractional_removesTopmostOfTargetedSlot() = runBlocking {
        registerLegoPiece()
        val world = testWorld()
        // Slot (0,0): three stacked pieces (yOffset 0,1,2).
        repeat(3) {
            BlockPlacer.placeAt(BlockPos(8, 5, 8), BlockType.LEGO_PIECE, 0, 0, 0, 0, world)
        }
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.breakTarget = BlockPos(8, 5, 8)
        session.breakTargetXOffset = 0
        session.breakTargetZOffset = 0
        breaker.tick(session)
        assertEquals(listOf(0, 1), world.getFractionalYOffsetsAt(8, 5, 8, 0, 0).sorted())
    }

    private fun registerAxeRequiredBlock(): BlockType {
        val type = BlockType("TEST_LOG")
        BlockRegistry.load(
            mapOf(
                type to
                    BlockDefinition(
                        hardness = 1f,
                        solid = true,
                        requiredEquipment = org.micoli.micraft.game.world.EquipmentCategory.AXE,
                    )))
        return type
    }

    @Test
    fun handleStart_requiredEquipmentMissing_ignores() {
        val type = registerAxeRequiredBlock()
        val world = WorldState(MapChunkGenerator(mapOf(Triple(8, 5, 8) to type)))
        val breaker = BlockBreaker(world, {}, noopWim())
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 5, 8)))
        assertNull(session.breakTarget)
    }

    @Test
    fun handleStart_requiredEquipmentInEitherHand_setsBreakTarget() {
        val type = registerAxeRequiredBlock()
        val world = WorldState(MapChunkGenerator(mapOf(Triple(8, 5, 8) to type)))
        val toolRegistry =
            mapOf(
                "iron_axe" to
                    org.micoli.micraft.game.equipment.ToolDefinition(
                        category = org.micoli.micraft.game.world.EquipmentCategory.AXE))
        val breaker = BlockBreaker(world, {}, noopWim(), toolRegistry = { toolRegistry })
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.state = session.state.copy(leftHandItem = "iron_axe")
        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 5, 8)))
        assertNotNull(session.breakTarget)
    }
}
