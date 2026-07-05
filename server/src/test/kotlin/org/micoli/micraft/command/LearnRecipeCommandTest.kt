package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.world.ItemType
import org.micoli.micraft.world.RecipeDefinition
import org.micoli.micraft.world.RecipeIngredient
import org.micoli.micraft.world.RecipeRegistry

private val LEARN_RECIPE =
    RecipeDefinition(
        giveType = "item",
        giveId = "COBBLESTONE",
        giveAmount = 1,
        ingredients = listOf(RecipeIngredient(ItemType("GRAVEL"), 2)),
    )

class LearnRecipeCommandTest {
    private val cmd = LearnRecipeCommand()

    private fun setup() {
        RecipeRegistry.load(mapOf("LEARN_TEST" to LEARN_RECIPE))
    }

    @Test
    fun blankArgs_sendsUsage() = runBlocking {
        setup()
        val session = testSession()
        cmd.execute(session, "", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun unknownRecipe_sendsUnknown() = runBlocking {
        setup()
        val session = testSession()
        cmd.execute(session, "DOESNOTEXIST", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("doesnotexist", ignoreCase = true) ||
                    it.message.contains("unknown") ||
                    it.message.contains("inconnu")
            })
    }

    @Test
    fun success_addsToKnownRecipes() = runBlocking {
        setup()
        val session = testSession()
        cmd.execute(session, "LEARN_TEST", testContext())
        assertTrue("LEARN_TEST" in session.knownRecipes)
    }

    @Test
    fun success_callsSavePlayer() = runBlocking {
        setup()
        val saved = mutableListOf<PlayerSession>()
        val session = testSession()
        cmd.execute(session, "LEARN_TEST", testContext(savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
    }

    @Test
    fun success_sendsRecipeSync() = runBlocking {
        setup()
        val session = testSession()
        cmd.execute(session, "LEARN_TEST", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.RecipeSync>().isNotEmpty())
    }

    @Test
    fun success_sendsLearned_notification() = runBlocking {
        setup()
        val session = testSession()
        cmd.execute(session, "LEARN_TEST", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("learn_test", ignoreCase = true) ||
                    it.message.contains("learned") ||
                    it.message.contains("appris")
            })
    }
}
