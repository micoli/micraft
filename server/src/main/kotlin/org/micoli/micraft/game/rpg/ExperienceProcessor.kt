package org.micoli.micraft.game.rpg

import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcTier
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ExperienceProcessor::class.java)

class ExperienceProcessor(
    @Volatile private var config: ExperienceConfigData,
    private val getSessions: () -> Collection<PlayerSession>,
    private val savePlayer: suspend (PlayerSession) -> Unit,
    private val subscribeToChannel: suspend (PlayerSession, String) -> Unit = { _, _ -> },
    private val broadcastCombatLog: suspend (String) -> Unit = {},
    onNpcLevelUp: suspend (NpcInstance, Int) -> Unit = { _, _ -> },
) {
    /** Wired post-construction (pet subsystem) to avoid a DI cycle. */
    @Volatile var onNpcLevelUp: suspend (NpcInstance, Int) -> Unit = onNpcLevelUp

    fun computeLevel(xp: Int, thresholds: List<Int>): Int {
        var level = 1
        var cumulative = 0
        for (threshold in thresholds) {
            cumulative += threshold
            if (xp >= cumulative) level++ else break
        }
        return level
    }

    private fun nextLevelXp(level: Int, thresholds: List<Int>): Int {
        val idx = level - 1
        return if (idx < thresholds.size) thresholds[idx] else Int.MAX_VALUE
    }

    private fun xpIntoCurrentLevel(xp: Int, level: Int, thresholds: List<Int>): Int {
        val cumulative = thresholds.take(level - 1).sum()
        return xp - cumulative
    }

    suspend fun grantXp(session: PlayerSession, amount: Int) {
        val charData = session.characterData ?: return
        val thresholds = config.progression.thresholds
        val oldLevel = charData.level
        val newXp = charData.xp + amount
        val newLevel = computeLevel(newXp, thresholds)
        val leveledUp = newLevel > oldLevel

        val updated = charData.copy(xp = newXp, level = newLevel)
        session.characterData = updated

        val nextXp = nextLevelXp(newLevel, thresholds)
        val xpIntoLevel = xpIntoCurrentLevel(newXp, newLevel, thresholds)
        session.send(
            ServerMessage.XpGained(
                xpGained = amount,
                totalXp = xpIntoLevel,
                level = newLevel,
                leveledUp = leveledUp,
                nextLevelXp = nextXp,
            ))

        subscribeToChannel(session, "game")
        session.send(
            ServerMessage.ChatMessage(
                channel = "game",
                sender = "",
                message = "+$amount XP (${xpIntoLevel} / $nextXp)",
            ))

        if (leveledUp) {
            val derived = DerivedStatsCalculator.compute(updated)
            session.send(ServerMessage.CharacterSync(updated, derived, updated.baseStats))
            session.send(ServerMessage.Notification("Level up! You are now level $newLevel"))
            session.send(
                ServerMessage.ChatMessage(
                    channel = "game",
                    sender = "",
                    message = "Level up! Now level $newLevel ($xpIntoLevel / $nextXp XP)",
                ))
            log.info(
                "Player {} leveled up {} → {}",
                session.state.name,
                oldLevel,
                newLevel,
            )
        }

        savePlayer(session)
    }

    suspend fun sendXpState(session: PlayerSession) {
        val charData = session.characterData ?: return
        if (session.state.rpgOptOut) return
        val thresholds = config.progression.thresholds
        val level = computeLevel(charData.xp, thresholds)
        val nextXp = nextLevelXp(level, thresholds)
        val xpIntoLevel = xpIntoCurrentLevel(charData.xp, level, thresholds)
        session.send(
            ServerMessage.XpGained(
                xpGained = 0,
                totalXp = xpIntoLevel,
                level = level,
                leveledUp = false,
                nextLevelXp = nextXp,
            ))
    }

    suspend fun grantXpToNpc(npc: NpcInstance, amount: Int): Boolean {
        val thresholds = config.progression.thresholds
        val oldLevel = npc.instanceLevel
        val oldMaxHp = npc.definition.computeMaxHp(oldLevel)
        val newXp = npc.xp + amount
        val newLevel = computeLevel(newXp, thresholds).coerceAtMost(npc.definition.maxLevel)
        val leveledUp = newLevel > oldLevel

        npc.xp = newXp
        npc.state = npc.state.copy(xp = newXp)

        if (leveledUp) {
            val newMaxHp = npc.definition.computeMaxHp(newLevel)
            val scaledHp =
                if (oldMaxHp > 0)
                    (newMaxHp * (npc.currentHp.toFloat() / oldMaxHp)).toInt().coerceAtLeast(1)
                else newMaxHp
            npc.instanceLevel = newLevel
            npc.maxHp = newMaxHp
            npc.currentHp = scaledHp
            npc.state =
                npc.state.copy(
                    level = newLevel,
                    xp = newXp,
                    currentHp = scaledHp,
                    maxHp = newMaxHp,
                )
            broadcastCombatLog("[m:${npc.state.name}] has grown stronger! (Level $newLevel)")
            log.info("NPC {} leveled up {} → {}", npc.state.name, oldLevel, newLevel)
            onNpcLevelUp(npc, newLevel)
        }
        return leveledUp
    }

    suspend fun grantXpToNpcForKill(predator: NpcInstance, prey: NpcInstance) {
        val baseXp =
            when (prey.definition.tier) {
                NpcTier.COMMON -> config.sources.commonPerLevel
                NpcTier.ELITE -> config.sources.elitePerLevel
                NpcTier.BOSS -> config.sources.bossPerLevel
            } * prey.definition.minLevel
        grantXpToNpc(predator, baseXp)
    }

    suspend fun onNpcKilled(npc: NpcInstance) {
        log.info(
            "onNpcKilled: npc={} tier={} lv={} contributors={}",
            npc.state.id.take(8),
            npc.definition.tier,
            npc.definition.minLevel,
            npc.damageContributors.keys,
        )
        val thresholds = config.progression.thresholds
        val baseXp =
            when (npc.definition.tier) {
                NpcTier.COMMON -> config.sources.commonPerLevel
                NpcTier.ELITE -> config.sources.elitePerLevel
                NpcTier.BOSS -> config.sources.bossPerLevel
            } * npc.definition.minLevel

        val contributors = npc.damageContributors.toMap()
        if (contributors.isEmpty()) {
            log.debug("NPC {} killed with no contributors, skipping XP", npc.state.id)
            return
        }

        val count = contributors.size
        val sessions = getSessions()

        val shareXp: Int
        if (count == 1 || !config.group.enabled) {
            shareXp = baseXp
            log.debug(
                "NPC {} (tier={} lv={}) killed solo by {}, baseXp={}",
                npc.state.id,
                npc.definition.tier,
                npc.definition.minLevel,
                contributors.keys.first(),
                baseXp,
            )
        } else {
            val bonus = config.group.bonusPerMember * (count - 1)
            shareXp = (baseXp / count.toDouble() * (1.0 + bonus)).toInt().coerceAtLeast(1)
            log.debug(
                "NPC {} (tier={} lv={}) killed by {} players, baseXp={} shareXp={} (bonus={:.0f}%)",
                npc.state.id,
                npc.definition.tier,
                npc.definition.minLevel,
                count,
                baseXp,
                shareXp,
                bonus * 100,
            )
        }

        log.debug("Active sessions: {}", sessions.map { it.id })
        for (contributorId in contributors.keys) {
            val session = sessions.find { it.id == contributorId }
            if (session == null) {
                log.warn("Contributor {} not found in active sessions", contributorId)
                continue
            }
            if (session.characterData == null) {
                log.debug("Contributor {} has no character, skipping XP", contributorId)
                continue
            }
            if (session.state.rpgOptOut) {
                log.debug("Contributor {} opted out of RPG, skipping XP", contributorId)
                continue
            }
            grantXp(session, shareXp)
        }
    }

    fun reload(config: ExperienceConfigData) {
        this.config = config
    }
}
