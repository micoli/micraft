package org.micoli.micraft.game

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

/**
 * A slash command typed by a `?gameSession=` player must act on that player's own [GameWorld], not
 * GameLoop's default one — otherwise `/group`, `/guild`, `/claim`… silently target the wrong world.
 */
class CommandContextRoutingTest {

    @Test
    fun `a gameSession-scoped session gets its world's subsystems in the command context`() {
        val gameLoop = GameLoop(testWorld(), e2eEnabled = true)
        val scoped = gameLoop.gameWorldRegistry.resolve("w1")
        assertNotSame(gameLoop.defaultWorld, scoped, "an unknown id spawned a dedicated world")

        val session = testSession(id = "p1", name = "Scout").apply { gameSessionId = "w1" }
        val ctx = gameLoop.commandContextFor(session)

        assertSame(scoped.groupManager, ctx.groupManager)
        assertSame(scoped.guildManager, ctx.guildManager)
        assertSame(scoped.guildRegistry, ctx.guildRegistry)
        assertSame(scoped.claimManager, ctx.claimManager)
        assertSame(scoped.tradeManager, ctx.tradeManager)
        assertSame(scoped.questManager, ctx.questManager)
        assertSame(scoped.weatherManager, ctx.weatherManager)
        assertSame(scoped.liquidManager, ctx.liquidManager)
        assertSame(scoped.chatService, ctx.chatService)
        assertSame(scoped.chatChannelManager, ctx.chatChannelManager)
        assertSame(scoped.world, ctx.world)
        assertNotSame(gameLoop.defaultWorld.groupManager, ctx.groupManager)
        assertNotSame(gameLoop.defaultWorld.chatService, ctx.chatService)
        assertNotSame(gameLoop.defaultWorld.chatChannelManager, ctx.chatChannelManager)

        // A custom channel created in one world is invisible to the others.
        scoped.chatChannelManager.registerChannel("wonly")
        assertTrue("wonly" in scoped.chatChannelManager.listKnownChannels())
        assertTrue("wonly" !in gameLoop.defaultWorld.chatChannelManager.listKnownChannels())
    }

    @Test
    fun `sessionByName finds a player living only in a dynamic world and scopes their context`() {
        val gameLoop = GameLoop(testWorld(), e2eEnabled = true)
        val w1 = gameLoop.gameWorldRegistry.resolve("w1")
        val zed = testSession(id = "z1", name = "Zed").apply { gameSessionId = "w1" }
        w1.sessions["z1"] = zed

        assertSame(zed, gameLoop.sessionByName("Zed"))
        assertNull(gameLoop.sessionByName("Nobody"))
        assertSame(w1.groupManager, gameLoop.commandContextFor(zed).groupManager)
    }

    @Test
    fun `a default-world session keeps the shared default command context`() {
        val gameLoop = GameLoop(testWorld(), e2eEnabled = true)
        val session = testSession(id = "p2", name = "Local") // gameSessionId stays null

        val ctx = gameLoop.commandContextFor(session)

        assertSame(gameLoop.defaultWorld.groupManager, ctx.groupManager)
        assertSame(gameLoop.commandContextFor(session), ctx, "the default context is reused as-is")
    }
}
