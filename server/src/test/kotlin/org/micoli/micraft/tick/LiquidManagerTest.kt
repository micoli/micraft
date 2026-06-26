package org.micoli.micraft.tick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.MapChunkGenerator
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldState

class LiquidManagerTest {

    private fun worldWith(vararg blocks: Pair<Triple<Int, Int, Int>, BlockType>): WorldState {
        val map = blocks.associate { (t, bt) -> t to bt }
        val world = WorldState(MapChunkGenerator(map))
        // Pre-generate chunk (0,0) so all calls use getOrGenerate consistently
        world.getOrGenerate(ChunkPos(0, 0))
        return world
    }

    private fun pos(x: Int, y: Int, z: Int) = Triple(x, y, z)

    private fun runTicks(
        manager: LiquidManager,
        count: Int,
        broadcast: suspend (ServerMessage) -> Unit = {},
    ) = runBlocking { repeat(count) { manager.tick(broadcast) } }

    @Test
    fun water_falls_into_air_below() {
        // WATER at y=10, AIR at y=9 (no solid floor immediately)
        val world =
            worldWith(
                pos(8, 10, 8) to BlockType.WATER,
                pos(8, 5, 8) to BlockType.STONE, // floor at y=5
            )
        val manager = LiquidManager(world)
        manager.activate(BlockPos(8, 10, 8), 0)

        val messages = mutableListOf<ServerMessage>()
        // viscosity=3 → needs 3 ticks of cooldown before spreading; activate() sets pendingTicks=0
        // so first tick processes
        runTicks(manager, 1) { messages.add(it) }

        // Water should have fallen to y=9
        assertEquals(BlockType.WATER, world.getBlock(8, 9, 8))
    }

    @Test
    fun water_does_not_fall_through_solid_floor() {
        // WATER at y=5, STONE at y=4
        val world =
            worldWith(
                pos(8, 5, 8) to BlockType.WATER,
                pos(8, 4, 8) to BlockType.STONE,
            )
        val manager = LiquidManager(world)
        manager.activate(BlockPos(8, 5, 8), 0)

        val messages = mutableListOf<ServerMessage>()
        runTicks(manager, 1) { messages.add(it) }

        // Water should NOT have moved below the solid floor
        assertEquals(BlockType.AIR, world.getBlock(8, 3, 8))
        assertEquals(BlockType.STONE, world.getBlock(8, 4, 8))
    }

    @Test
    fun water_spreads_horizontally_when_floor_below() {
        // WATER at y=5, STONE at y=4 on all sides, AIR neighbors at y=5
        val world =
            worldWith(
                pos(8, 5, 8) to BlockType.WATER,
                pos(7, 4, 8) to BlockType.STONE,
                pos(9, 4, 8) to BlockType.STONE,
                pos(8, 4, 7) to BlockType.STONE,
                pos(8, 4, 9) to BlockType.STONE,
                pos(8, 4, 8) to BlockType.STONE, // floor under source
            )
        val manager = LiquidManager(world)
        manager.activate(BlockPos(8, 5, 8), 0)

        val messages = mutableListOf<ServerMessage>()
        runTicks(manager, 1) { messages.add(it) }

        // At least one horizontal neighbor should have water
        val spreadCount =
            listOf(
                    world.getBlock(7, 5, 8),
                    world.getBlock(9, 5, 8),
                    world.getBlock(8, 5, 7),
                    world.getBlock(8, 5, 9),
                )
                .count { it == BlockType.WATER }
        assert(spreadCount > 0) { "Water should have spread to at least one horizontal neighbor" }
    }

    @Test
    fun water_does_not_spread_beyond_max_flow_distance() {
        // WATER at y=5 with distance already at MAX (7), STONE floor
        val world =
            worldWith(
                pos(8, 5, 8) to BlockType.WATER,
                pos(8, 4, 8) to BlockType.STONE,
                pos(7, 4, 8) to BlockType.STONE,
            )
        val manager = LiquidManager(world)
        manager.activate(BlockPos(8, 5, 8), 7) // already at max distance

        val messages = mutableListOf<ServerMessage>()
        runTicks(manager, 1) { messages.add(it) }

        // Should NOT spread horizontally (distance = 7 = MAX)
        assertEquals(BlockType.AIR, world.getBlock(7, 5, 8))
        assertEquals(BlockType.AIR, world.getBlock(9, 5, 8))
    }

    @Test
    fun viscosity_delays_propagation() {
        // viscosity=3, so activate with pendingTicks=0 → tick 1 processes, then re-queues with
        // pendingTicks=3
        // Next spread only happens after 3 more ticks
        val world =
            worldWith(
                pos(8, 10, 8) to BlockType.WATER,
                pos(8, 5, 8) to BlockType.STONE,
            )
        val manager = LiquidManager(world)
        manager.activate(BlockPos(8, 10, 8), 0)

        // After tick 1: water falls to y=9, new block queued with pendingTicks=3
        runTicks(manager, 1)
        assertEquals(BlockType.WATER, world.getBlock(8, 9, 8))

        // After 2 more ticks (total 3): still at y=9 (pendingTicks not yet 0)
        runTicks(manager, 2)
        assertEquals(BlockType.AIR, world.getBlock(8, 8, 8))

        // After 1 more tick (total 4 = 1 + 3): falls to y=8
        runTicks(manager, 1)
        assertEquals(BlockType.WATER, world.getBlock(8, 8, 8))
    }

    @Test
    fun activate_triggers_spread_on_block_break() {
        // Water at y=5, solid block broken at (9,5,8) — adjacent water should activate
        val world =
            worldWith(
                pos(8, 5, 8) to BlockType.WATER,
                pos(8, 4, 8) to BlockType.STONE,
                pos(9, 4, 8) to BlockType.STONE,
            )
        val manager = LiquidManager(world)
        // Simulate block break at (9,5,8) — block is now AIR, activate adjacent water
        manager.activate(BlockPos(8, 5, 8), 0)

        val messages = mutableListOf<ServerMessage>()
        runTicks(manager, 1) { messages.add(it) }

        // Water should spread to (9,5,8) which is now AIR
        assertEquals(BlockType.WATER, world.getBlock(9, 5, 8))
    }
}
