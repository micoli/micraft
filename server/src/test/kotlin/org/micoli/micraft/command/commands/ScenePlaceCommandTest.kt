package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.scene.SceneRegistry
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class ScenePlaceCommandTest {
    private val command = ScenePlaceCommand()

    private fun sceneRegistryWithScene(): Pair<SceneRegistry, String> {
        val registry = SceneRegistry(null)
        val scene =
            registry.create(name = "Room", width = 1, height = 1, depth = 1, ownerName = "Alice")
        scene.setBlock(
            0, 0, 0, BlockRegistry.wireIndex(BlockType.STONE).toByte(), BlockState.pack(0, 0))
        return registry to scene.id
    }

    @Test
    fun `missing scenes registry notifies unavailable`() = runBlocking {
        val session = testSession()
        command.execute(session, "id 0 0 0 0", testContext(scenes = null))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun `wrong arg count is rejected`() = runBlocking {
        val (registry, id) = sceneRegistryWithScene()
        val session = testSession()
        command.execute(session, id, testContext(scenes = registry))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun `unknown scene id notifies and does not mutate world`() = runBlocking {
        val (registry, _) = sceneRegistryWithScene()
        val world = testWorld()
        val session = testSession()
        command.execute(session, "nope 0 0 0 0", testContext(world = world, scenes = registry))
        assertEquals(BlockType.AIR, world.getBlock(0, 0, 0))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun `valid placement stamps scene into world`() = runBlocking {
        val (registry, id) = sceneRegistryWithScene()
        val world = testWorld()
        val session = testSession()
        command.execute(session, "$id 0 10 5 10", testContext(world = world, scenes = registry))
        assertEquals(BlockType.STONE, world.getBlock(10, 5, 10))
    }
}
