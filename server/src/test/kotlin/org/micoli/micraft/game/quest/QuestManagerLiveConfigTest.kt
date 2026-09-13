package org.micoli.micraft.game.quest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.npc.NpcDeathCause
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.quest.QuestProgress
import org.micoli.micraft.quest.QuestStatus
import org.micoli.micraft.support.testSession

/**
 * Exercises the real shipped config (NpcRegistryLoader + QuestRegistryLoader, not hand-built test
 * fixtures) end to end: spawn a real "fox", kill it as a player, verify quest progress increments.
 */
class QuestManagerLiveConfigTest {

    @Test
    fun `killing a real fox increments the fox kill-quest progress`() = runBlocking {
        val quests = QuestRegistryLoader().load()
        val foxQuestEntry =
            assertNotNull(
                quests.entries.firstOrNull { (_, def) ->
                    def.objectives.any { it.npcType == "fox" }
                },
                "no shipped quest currently targets npcType 'fox'")
        val (foxQuestId, foxQuestDef) = foxQuestEntry

        val player = testSession(id = "p1", name = "Hunter")
        player.state =
            player.state.copy(
                quests = mapOf(foxQuestId to QuestProgress(status = QuestStatus.IN_PROGRESS)))

        lateinit var questManager: QuestManager
        val npcManager =
            NpcManager(
                broadcast = {},
                getSessions = { listOf(player) },
                onNpcKilled = { npc, cause, _ ->
                    if (cause == NpcDeathCause.KILLED) questManager.onNpcKilled(npc)
                },
            )
        questManager =
            QuestManager(getSessions = { listOf(player) }, savePlayer = {}).also {
                it.reloadDefinitions(quests)
            }

        val npcDefs = NpcRegistryLoader().load()
        assertNotNull(npcDefs["fox"], "no shipped NPC entity registered under type 'fox'")
        npcManager.loadDefinitions(npcDefs)
        val fox = npcManager.spawnNpc("Rusty", "fox", Vec3(0f, 0f, 0f))

        npcManager.applyDamage(fox.state.id, fox.currentHp + 999, player.id)

        val progress = player.state.quests[foxQuestId]
        assertEquals(
            1,
            progress?.progress?.get("fox"),
            "expected fox kill count to be 1, quest def was: $foxQuestDef")
    }
}
