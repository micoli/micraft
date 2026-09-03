package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.actionblock.ActionBlockRegistry
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class ActionBlockDeleteCommandTest {
    private val cmd = ActionBlockDeleteCommand()

    @Test
    fun ownerCanDelete_broadcastsRemove() = runBlocking {
        val registry = ActionBlockRegistry(null)
        val block = registry.create(BlockPos(1, 64, 2), "Alice") { BlockType.STONE }!!
        val broadcasts = mutableListOf<ServerMessage>()
        cmd.execute(
            testSession(name = "Alice"),
            block.name,
            testContext(actionBlockRegistry = registry, broadcast = { broadcasts.add(it) }),
        )
        assertFalse(registry.isActionBlock(block.pos))
        assertTrue(broadcasts.any { it is ServerMessage.ActionBlockRemove })
    }

    @Test
    fun nonOwnerWithoutPermission_isRejected() = runBlocking {
        val registry = ActionBlockRegistry(null)
        val block = registry.create(BlockPos(1, 64, 2), "Alice") { BlockType.STONE }!!
        cmd.execute(
            testSession(name = "Bob"), block.name, testContext(actionBlockRegistry = registry))
        assertTrue(registry.isActionBlock(block.pos))
    }
}
