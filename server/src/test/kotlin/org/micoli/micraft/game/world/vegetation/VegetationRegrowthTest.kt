package org.micoli.micraft.game.world.vegetation

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testWorld

private const val GROUND_Y = 4
private const val X = 8
private const val Z = 8

/** Grass host at (8,4,8) so the cell above can carry a plant. */
private fun grazingWorld(): WorldState {
    val world = testWorld(Triple(X, GROUND_Y, Z))
    world.applyChange(BlockChange(BlockPos(X, GROUND_Y, Z), BlockType.GRASS))
    return world
}

private fun manager(world: WorldState): VegetationManager =
    VegetationManager(
        world,
        VegetationConfig(),
        createTempDirectory("veg-regrowth").resolve("state.yaml"),
    )

/** Drive enough ticks for the slowest configured regrowth to complete. */
private suspend fun VegetationManager.tickFor(times: Int) {
    repeat(times) { tick {} }
}

class VegetationRegrowthTest {

    private val plantPos = BlockPos(X, GROUND_Y + 1, Z)

    @Test
    fun grazedWeed_growsBackAfterItsDelay() = runBlocking {
        val world = grazingWorld()
        val manager = manager(world)
        world.applyChange(BlockChange(plantPos, BlockType.AIR))

        manager.onGrazed(plantPos, BlockType.WEED)
        assertEquals(1, manager.regrowingCount(), "grazing must schedule a regrowth")

        manager.tickFor(200)
        assertEquals(
            BlockType.AIR,
            world.getBlockIfLoaded(X, GROUND_Y + 1, Z),
            "regrowth must not be instant — herbivores would never run out of food")

        manager.tickFor(5_000)
        assertEquals(BlockType.WEED, world.getBlockIfLoaded(X, GROUND_Y + 1, Z))
        assertEquals(0, manager.regrowingCount())
    }

    @Test
    fun regrowth_isBroadcastSoClientsSeeIt() = runBlocking {
        val world = grazingWorld()
        val manager = manager(world)
        world.applyChange(BlockChange(plantPos, BlockType.AIR))
        manager.onGrazed(plantPos, BlockType.WEED)

        val messages = mutableListOf<ServerMessage>()
        repeat(5_000) { manager.tick { messages.add(it) } }

        val changes = messages.filterIsInstance<ServerMessage.WorldUpdate>().flatMap { it.changes }
        assertTrue(
            changes.any { it.pos == plantPos && it.type == BlockType.WEED },
            "the regrown block must reach clients")
    }

    @Test
    fun unknownGrazedBlock_schedulesNothing() {
        val world = grazingWorld()
        val manager = manager(world)
        manager.onGrazed(plantPos, BlockType.STONE)
        assertEquals(0, manager.regrowingCount())
    }

    @Test
    fun withoutVegetationHost_nothingRegrows() {
        val world = testWorld(Triple(X, GROUND_Y, Z))
        world.applyChange(BlockChange(BlockPos(X, GROUND_Y, Z), BlockType.STONE))
        val manager = manager(world)
        manager.onGrazed(BlockPos(X, GROUND_Y + 1, Z), BlockType.WEED)
        assertEquals(0, manager.regrowingCount(), "stone is not a vegetation host")
    }

    @Test
    fun cellTakenMeanwhile_cancelsTheRegrowth() = runBlocking {
        val world = grazingWorld()
        val manager = manager(world)
        world.applyChange(BlockChange(plantPos, BlockType.AIR))
        manager.onGrazed(plantPos, BlockType.WEED)

        // something solid now stands there
        world.applyChange(BlockChange(plantPos, BlockType.STONE))
        manager.tickFor(100)

        assertEquals(0, manager.regrowingCount())
        assertEquals(BlockType.STONE, world.getBlockIfLoaded(X, GROUND_Y + 1, Z))
    }

    @Test
    fun pendingRegrowth_survivesSaveAndLoad() = runBlocking {
        val world = grazingWorld()
        world.applyChange(BlockChange(plantPos, BlockType.AIR))
        val savePath: Path = createTempDirectory("veg-save").resolve("state.yaml")
        val first = VegetationManager(world, VegetationConfig(), savePath)
        first.onGrazed(plantPos, BlockType.WEED)
        first.save()

        val second = VegetationManager(world, VegetationConfig(), savePath)
        second.load()
        assertEquals(1, second.regrowingCount(), "a restart must not lose pending regrowth")

        second.tickFor(5_000)
        assertEquals(BlockType.WEED, world.getBlockIfLoaded(X, GROUND_Y + 1, Z))
    }

    @Test
    fun defaultRules_coverBothGrazableBlocks() {
        val rules = VegetationConfig().data.regrowth
        assertTrue(rules.any { it.grazed == BlockType.WEED.id }, "WEED must grow back")
        assertTrue(rules.any { it.grazed == BlockType.FLOWER.id }, "FLOWER must grow back")
        rules.forEach { rule ->
            assertTrue(rule.minTicks > 0 && rule.maxTicks >= rule.minTicks, "sane delay: $rule")
        }
    }
}
