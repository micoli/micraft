package org.micoli.micraft.game.rpg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcTier
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession

class ExperienceProcessorTest {

    private val thresholds = listOf(300, 900, 2700, 6500)

    private val config =
        ExperienceConfigData(
            progression = ProgressionConfig(thresholds = thresholds),
            sources = SourcesConfig(commonPerLevel = 50, elitePerLevel = 200, bossPerLevel = 1000),
            group = XpGroupConfig(enabled = true, bonusPerMember = 0.10),
        )

    private fun processor(sessions: List<PlayerSession> = emptyList()) =
        ExperienceProcessor(
            config = config,
            getSessions = { sessions },
            savePlayer = {},
        )

    private fun charData(xp: Int = 0, level: Int = 1) =
        CharacterData(
            id = "c1",
            name = "Hero",
            characterClass = CharacterClass.WARRIOR,
            baseStats = BaseStats(str = 10),
            currentHp = 20,
            currentMana = 10,
            xp = xp,
            level = level,
        )

    // ── computeLevel ─────────────────────────────────────────────────────────

    @Test
    fun `computeLevel returns 1 for 0 xp`() {
        assertEquals(1, processor().computeLevel(0, thresholds))
    }

    @Test
    fun `computeLevel returns 1 just below first threshold`() {
        assertEquals(1, processor().computeLevel(299, thresholds))
    }

    @Test
    fun `computeLevel returns 2 at first threshold`() {
        assertEquals(2, processor().computeLevel(300, thresholds))
    }

    @Test
    fun `computeLevel returns 3 when enough xp for two levels`() {
        assertEquals(3, processor().computeLevel(300 + 900, thresholds))
    }

    @Test
    fun `computeLevel caps at max level defined by thresholds`() {
        val maxXp = thresholds.sum() + 1_000_000
        assertEquals(thresholds.size + 1, processor().computeLevel(maxXp, thresholds))
    }

    // ── grantXp ──────────────────────────────────────────────────────────────

    @Test
    fun `grantXp adds xp and sends XpGained`() = runBlocking {
        val session = testSession(id = "s1")
        session.state = session.state.copy(rpgOptOut = false)
        session.characterData = charData(xp = 0)

        processor(listOf(session)).grantXp(session, 100)

        val xpMsg = session.sent.filterIsInstance<ServerMessage.XpGained>().last()
        assertEquals(100, xpMsg.totalXp)
        assertEquals(100, xpMsg.xpGained)
        assertEquals(1, xpMsg.level)
        assertFalse(xpMsg.leveledUp)
        assertEquals(300, xpMsg.nextLevelXp)
    }

    @Test
    fun `grantXp triggers level up and sends CharacterSync + Notification`() = runBlocking {
        val session = testSession(id = "s2")
        session.state = session.state.copy(rpgOptOut = false)
        session.characterData = charData(xp = 250)

        processor(listOf(session)).grantXp(session, 100)

        val xpMsg = session.sent.filterIsInstance<ServerMessage.XpGained>().last()
        assertTrue(xpMsg.leveledUp)
        assertEquals(2, xpMsg.level)
        assertEquals(2, session.characterData?.level)

        assertTrue(session.sent.any { it is ServerMessage.CharacterSync })
        assertTrue(session.sent.any { it is ServerMessage.Notification })
    }

    // ── onNpcKilled — group XP ────────────────────────────────────────────────

    @Test
    fun `onNpcKilled solo kill gives full base xp`() = runBlocking {
        val session = testSession(id = "solo")
        session.state = session.state.copy(rpgOptOut = false)
        session.characterData = charData()

        val npc = makeNpc(level = 2, tier = NpcTier.COMMON)
        npc.damageContributors["solo"] = 100

        processor(listOf(session)).onNpcKilled(npc)

        val xpMsg = session.sent.filterIsInstance<ServerMessage.XpGained>().last()
        assertEquals(100, xpMsg.xpGained) // 50 * level=2 = 100
    }

    @Test
    fun `onNpcKilled 4 players each get share with group bonus`() = runBlocking {
        val sessions =
            (1..4).map { i ->
                testSession(id = "p$i").also {
                    it.state = it.state.copy(rpgOptOut = false)
                    it.characterData = charData()
                }
            }
        val npc = makeNpc(level = 5, tier = NpcTier.ELITE)
        sessions.forEach { npc.damageContributors[it.id] = 25 }

        processor(sessions).onNpcKilled(npc)

        // base = 200 * 5 = 1000, share = 1000/4 * (1 + 3*0.1) = 250 * 1.3 = 325
        val xpMsg = sessions[0].sent.filterIsInstance<ServerMessage.XpGained>().last()
        assertEquals(325, xpMsg.xpGained)
    }

    @Test
    fun `onNpcKilled rpgOptOut players get no xp`() = runBlocking {
        val session = testSession(id = "opted")
        session.state = session.state.copy(rpgOptOut = true)
        session.characterData = charData()

        val npc = makeNpc()
        npc.damageContributors["opted"] = 100

        processor(listOf(session)).onNpcKilled(npc)

        assertTrue(session.sent.filterIsInstance<ServerMessage.XpGained>().isEmpty())
    }

    // ── sendXpState ───────────────────────────────────────────────────────────

    @Test
    fun `sendXpState sends XpGained with xpGained=0`() = runBlocking {
        val session = testSession(id = "sx1")
        session.state = session.state.copy(rpgOptOut = false)
        session.characterData = charData(xp = 500, level = 2)

        processor(listOf(session)).sendXpState(session)

        val xpMsg = session.sent.filterIsInstance<ServerMessage.XpGained>().last()
        assertEquals(0, xpMsg.xpGained)
        assertEquals(500, xpMsg.totalXp)
        assertEquals(2, xpMsg.level)
        assertFalse(xpMsg.leveledUp)
        assertEquals(900, xpMsg.nextLevelXp)
    }

    @Test
    fun `sendXpState skips rpgOptOut session`() = runBlocking {
        val session = testSession(id = "sx2")
        session.state = session.state.copy(rpgOptOut = true)
        session.characterData = charData(xp = 500)

        processor(listOf(session)).sendXpState(session)

        assertTrue(session.sent.filterIsInstance<ServerMessage.XpGained>().isEmpty())
    }

    @Test
    fun `sendXpState skips session with no character`() = runBlocking {
        val session = testSession(id = "sx3")
        session.state = session.state.copy(rpgOptOut = false)
        session.characterData = null

        processor(listOf(session)).sendXpState(session)

        assertTrue(session.sent.filterIsInstance<ServerMessage.XpGained>().isEmpty())
    }

    private fun makeNpc(level: Int = 1, tier: NpcTier = NpcTier.COMMON): NpcInstance {
        val def =
            NpcDefinition(
                type = "goblin",
                behavior = StaticNpcBehavior(),
                behaviorKey = "static",
                bbmodelFile = "goblin.bbmodel",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 0f,
                wanderRadius = 0f,
                hp = 20,
                aggroMode = AggroMode.PASSIVE,
                level = level,
                tier = tier,
            )
        val state =
            NpcState(
                id = "npc1",
                name = "Goblin",
                pos = Vec3(0f, 0f, 0f),
                yaw = 0f,
                type = "goblin",
                currentHp = 0,
                maxHp = 20,
            )
        return NpcInstance(state = state, definition = def, spawnPos = Vec3(0f, 0f, 0f))
    }
}
