package org.micoli.micraft.game.armor

import kotlin.random.Random
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ArmorLootGranter::class.java)

/**
 * Rolls an NPC's [org.micoli.micraft.game.npc.NpcDefinition.armorLoot] on death and, on a hit,
 * grants the piece to one of the players who damaged it — same contributor lookup as
 * [org.micoli.micraft.game.quest.QuestManager.onNpcKilled], so credit for a kill is consistent
 * across XP, quests and loot.
 */
object ArmorLootGranter {
    suspend fun grant(
        npc: NpcInstance,
        armorRegistry: Map<String, ArmorDefinition>,
        getSessions: () -> Collection<PlayerSession>,
        savePlayer: (PlayerSession) -> Unit,
        i18n: I18nConfig? = null,
        random: Random = Random,
    ) {
        if (npc.definition.armorLoot.isEmpty()) return
        val contributorId = npc.damageContributors.keys.firstOrNull() ?: return
        val session = getSessions().find { it.id == contributorId } ?: return

        for (drop in npc.definition.armorLoot) {
            if (random.nextInt(100) >= drop.dropRate) continue
            if (armorRegistry[drop.armor] == null) {
                log.warn("NPC '{}' drops unknown armor '{}'", npc.state.type, drop.armor)
                continue
            }
            if (drop.armor in session.state.ownedArmors) continue
            session.state = session.state.copy(ownedArmors = session.state.ownedArmors + drop.armor)
            savePlayer(session)
            session.send(
                ServerMessage.Notification(
                    i18n?.t(session.state.language, "loot:server:armor_dropped", drop.armor)
                        ?: "You found ${drop.armor}!"))
            return
        }
    }
}
