package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.quest.QuestDefinition
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestType
import org.micoli.micraft.game.recipe.RecipeRegistry
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.RecipeDefinition
import org.micoli.micraft.game.world.RecipeIngredient
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.quest.QuestStatus
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

private val TEST_RECIPE =
    RecipeDefinition(
        giveType = "item",
        giveId = "COBBLESTONE",
        giveAmount = 2,
        ingredients =
            listOf(RecipeIngredient(ItemType("GRAVEL"), 3), RecipeIngredient(ItemType("SAND"), 1)),
    )

class DoCraftCommandTest {
    private val cmd = DoCraftCommand()

    private fun setupRegistry() {
        RecipeRegistry.load(mapOf("TEST_RECIPE" to TEST_RECIPE))
    }

    @Test
    fun unknownRecipe_sendsError() = runBlocking {
        setupRegistry()
        val session = testSession()
        session.knownRecipes.add("TEST_RECIPE")
        cmd.execute(session, "NONEXISTENT 1", testContext())
        assertTrue(
            session.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("nonexistent", ignoreCase = true) ||
                    it.message.contains("craft", ignoreCase = true)
            })
    }

    @Test
    fun notKnown_sendsError() = runBlocking {
        setupRegistry()
        val session = testSession()
        cmd.execute(session, "TEST_RECIPE 1", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
        assertEquals(0, session.inventory[ItemType("COBBLESTONE")] ?: 0)
    }

    @Test
    fun insufficientIngredients_sendsError() = runBlocking {
        setupRegistry()
        val session = testSession()
        session.knownRecipes.add("TEST_RECIPE")
        session.inventory[ItemType("GRAVEL")] = 1
        session.inventory[ItemType("SAND")] = 1
        cmd.execute(session, "TEST_RECIPE 1", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
        assertEquals(0, session.inventory[ItemType("COBBLESTONE")] ?: 0)
    }

    @Test
    fun craft_deductsIngredientsAndAddsResult() = runBlocking {
        setupRegistry()
        val session = testSession()
        session.knownRecipes.add("TEST_RECIPE")
        session.inventory[ItemType("GRAVEL")] = 6
        session.inventory[ItemType("SAND")] = 2
        cmd.execute(session, "TEST_RECIPE 2", testContext())
        assertEquals(4, session.inventory[ItemType("COBBLESTONE")])
        assertEquals(0, session.inventory[ItemType("GRAVEL")] ?: 0)
        assertEquals(0, session.inventory[ItemType("SAND")] ?: 0)
    }

    @Test
    fun craft_sendsInventoryUpdate() = runBlocking {
        setupRegistry()
        val session = testSession()
        session.knownRecipes.add("TEST_RECIPE")
        session.inventory[ItemType("GRAVEL")] = 3
        session.inventory[ItemType("SAND")] = 1
        cmd.execute(session, "TEST_RECIPE 1", testContext())
        assertTrue(session.sent.any { it is ServerMessage.InventoryUpdate })
    }

    @Test
    fun craft_callsSavePlayer() = runBlocking {
        setupRegistry()
        val saved = mutableListOf<PlayerSession>()
        val session = testSession()
        session.knownRecipes.add("TEST_RECIPE")
        session.inventory[ItemType("GRAVEL")] = 3
        session.inventory[ItemType("SAND")] = 1
        cmd.execute(session, "TEST_RECIPE 1", testContext(savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
    }

    @Test
    fun craft_updatesFetchQuestProgress() = runBlocking {
        setupRegistry()
        val session = testSession(id = "player-1")
        session.knownRecipes.add("TEST_RECIPE")
        session.inventory[ItemType("GRAVEL")] = 3
        session.inventory[ItemType("SAND")] = 1
        val qm = QuestManager(getSessions = { listOf(session) }, savePlayer = {})
        qm.reloadDefinitions(
            mapOf(
                "cobble_run" to
                    QuestDefinition(
                        id = "cobble_run",
                        title = "Cobble Run",
                        description = "Craft cobblestone.",
                        type = QuestType.FETCH,
                        itemType = "COBBLESTONE",
                        requiredCount = 2,
                    )))
        qm.accept(session, "cobble_run")
        cmd.execute(session, "TEST_RECIPE 1", testContext(questManager = qm))
        assertEquals(QuestStatus.COMPLETED, session.state.quests["cobble_run"]?.status)
    }

    @Test
    fun craft_partialIngredients_removesCompletely() = runBlocking {
        setupRegistry()
        val session = testSession()
        session.knownRecipes.add("TEST_RECIPE")
        session.inventory[ItemType("GRAVEL")] = 3
        session.inventory[ItemType("SAND")] = 1
        cmd.execute(session, "TEST_RECIPE 1", testContext())
        assertEquals(null, session.inventory[ItemType("GRAVEL")])
        assertEquals(null, session.inventory[ItemType("SAND")])
    }
}
