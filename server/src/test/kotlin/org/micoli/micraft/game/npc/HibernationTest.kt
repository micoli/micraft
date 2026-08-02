package org.micoli.micraft.game.npc

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

private const val BEAR = "polar_bear"

/** Sleeps 8 in-game hours (a third of a day) out of every 3 game days. */
private fun bearDef(
    hoursPerCycle: Double = 8.0,
    cycleDays: Double = 3.0,
    wakeOnDamage: Boolean = true,
): NpcDefinition =
    NpcDefinition(
        type = BEAR,
        behavior = RandomMovableNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 3f,
        wanderRadius = 12f,
        aggroMode = AggroMode.AGGRESSIVE,
        aggroRange = 15f,
        hibernation =
            HibernationConfig(
                hoursPerCycle = hoursPerCycle,
                cycleDays = cycleDays,
                wakeOnDamage = wakeOnDamage,
            ),
    )

private class Fixture(def: NpcDefinition = bearDef()) {
    val combatLog = mutableListOf<String>()
    var gameDay: Double = 0.0
    var sessions: List<PlayerSession> = emptyList()
    val manager =
        NpcManager(
            broadcast = {},
            getSessions = { sessions },
            broadcastCombatLog = { combatLog.add(it) },
        )
    val hibernation = HibernationProcessor(manager) { gameDay }

    init {
        manager.loadDefinitions(mapOf(BEAR to def))
    }

    fun combat() =
        CombatProcessor(
            config = CombatConfigData(),
            attackRegistry = emptyMap(),
            armorRegistry = emptyMap(),
            classRegistry = emptyMap(),
            npcManager = manager,
            getSessions = { sessions },
            broadcastCombatLog = {},
            subscribeToChannel = { _, _ -> },
            i18n = testI18n(),
            savePlayer = {},
        )

    /** Moves the clock to the first sleep window of this NPC and runs the processor. */
    fun sleep(instance: NpcInstance) {
        val config = instance.definition.hibernation!!
        gameDay = config.cycleDays - config.offsetFor(instance.state.id)
        hibernation.tick()
    }
}

class HibernationTest {
    @Test
    fun config_windowCoversExactlyTheConfiguredHours() {
        val config = HibernationConfig(hoursPerCycle = 12.0, cycleDays = 4.0)
        assertEquals(0.5, config.durationDays)
        assertTrue(config.isInWindow(gameDay = 0.0, offsetDays = 0.0))
        assertTrue(config.isInWindow(gameDay = 0.49, offsetDays = 0.0))
        assertFalse(config.isInWindow(gameDay = 0.51, offsetDays = 0.0))
        assertFalse(config.isInWindow(gameDay = 3.9, offsetDays = 0.0))
        // Next cycle.
        assertTrue(config.isInWindow(gameDay = 4.2, offsetDays = 0.0))
    }

    @Test
    fun config_disabledWhenHoursOrCycleIsZero() {
        assertFalse(HibernationConfig(hoursPerCycle = 0.0, cycleDays = 3.0).enabled)
        assertFalse(HibernationConfig(hoursPerCycle = 8.0, cycleDays = 0.0).enabled)
        assertFalse(HibernationConfig(hoursPerCycle = 8.0, cycleDays = 0.0).isInWindow(0.0, 0.0))
    }

    @Test
    fun config_offsetIsStablePerNpcAndInsideTheCycle() {
        val config = HibernationConfig(hoursPerCycle = 8.0, cycleDays = 3.0)
        val offset = config.offsetFor("abc-123")
        assertEquals(offset, config.offsetFor("abc-123"))
        assertTrue(offset >= 0.0 && offset < config.cycleDays)
    }

    @Test
    fun processor_sleepsInsideWindowAndWakesOutsideIt() = runBlocking {
        val f = Fixture()
        val bear = f.manager.spawnNpc("Ted", BEAR, Vec3(0f, 5f, 0f))
        f.sleep(bear)
        assertTrue(bear.hibernating)

        // Half a cycle later the window is long over.
        f.gameDay += bear.definition.hibernation!!.cycleDays / 2
        f.hibernation.tick()
        assertFalse(bear.hibernating)
    }

    @Test
    fun processor_dropsTargetsWhenFallingAsleep() = runBlocking {
        val f = Fixture()
        val bear = f.manager.spawnNpc("Ted", BEAR, Vec3(0f, 5f, 0f))
        bear.aggroTarget = "player-1"
        bear.npcAggroTarget = "npc-1"
        f.sleep(bear)
        assertNull(bear.aggroTarget)
        assertNull(bear.npcAggroTarget)
    }

    @Test
    fun tickAggro_hibernatingNpcDoesNotAggroNearbyPlayer() = runBlocking {
        val f = Fixture()
        val session = testSession(pos = Vec3(1f, 5f, 1f))
        f.sessions = listOf(session)
        val bear = f.manager.spawnNpc("Ted", BEAR, Vec3(0f, 5f, 0f))
        f.sleep(bear)

        f.manager.tickAggro(f.sessions, f.combat())
        assertNull(bear.aggroTarget)

        // Same player, awake bear: aggro happens.
        bear.hibernating = false
        f.manager.tickAggro(f.sessions, f.combat())
        assertEquals(session.id, bear.aggroTarget)
    }

    @Test
    fun behavior_hibernatingNpcDoesNotWander() {
        val world: WorldState = testWorld(Triple(8, 4, 8))
        val f = Fixture()
        val bear = runBlocking { f.manager.spawnNpc("Ted", BEAR, Vec3(8.5f, 5f, 8.5f)) }
        bear.vy = 0f
        f.sleep(bear)
        val before = bear.state.pos
        repeat(20) { RandomMovableNpcBehavior().tick(bear, world, NpcTickContext.live) }
        assertEquals(before, bear.state.pos)
    }

    @Test
    fun applyDamage_wakesTheNpcForTheRestOfTheWindow() = runBlocking {
        val f = Fixture()
        val session = testSession(pos = Vec3(1f, 5f, 1f))
        f.sessions = listOf(session)
        val bear = f.manager.spawnNpc("Ted", BEAR, Vec3(0f, 5f, 0f))
        f.sleep(bear)
        assertTrue(bear.hibernating)

        f.manager.applyDamage(bear.state.id, 3, session.id)
        assertFalse(bear.hibernating)
        assertTrue(bear.hibernationWakeForced)
        assertTrue(f.combatLog.any { it.contains("wakes up") })

        // Still inside the window, but it must not fall back asleep.
        f.hibernation.tick()
        assertFalse(bear.hibernating)

        // Once the window is over the forced wake-up is cleared and the next one sleeps again.
        f.gameDay += bear.definition.hibernation!!.cycleDays / 2
        f.hibernation.tick()
        assertFalse(bear.hibernationWakeForced)
        f.sleep(bear)
        assertTrue(bear.hibernating)
    }

    @Test
    fun applyDamage_wakeOnDamageDisabled_keepsSleeping() = runBlocking {
        val f = Fixture(bearDef(wakeOnDamage = false))
        val session = testSession(pos = Vec3(1f, 5f, 1f))
        f.sessions = listOf(session)
        val bear = f.manager.spawnNpc("Ted", BEAR, Vec3(0f, 5f, 0f))
        f.sleep(bear)

        f.manager.applyDamage(bear.state.id, 3, session.id)
        assertTrue(bear.hibernating)
        assertNull(bear.aggroTarget)
    }

    @Test
    fun processor_ignoresNpcWithoutHibernationConfig() = runBlocking {
        val f = Fixture(bearDef().copy(hibernation = null))
        val bear = f.manager.spawnNpc("Ted", BEAR, Vec3(0f, 5f, 0f))
        f.gameDay = 0.0
        f.hibernation.tick()
        assertFalse(bear.hibernating)
        Unit
    }

    @Test
    fun yaml_hibernationSectionIsDecodedOnTheEntityEntry() {
        val entry =
            Yaml.default.decodeFromString(
                NpcYamlEntry.serializer(),
                """
                behavior: random_movable
                hibernation:
                  hoursPerCycle: 8.0
                  cycleDays: 3.0
                  wakeOnDamage: true
                """
                    .trimIndent())
        assertEquals(8.0, entry.hibernation?.hoursPerCycle)
        assertEquals(3.0, entry.hibernation?.cycleDays)
        assertTrue(entry.hibernation?.wakeOnDamage == true)
    }

    @Test
    fun yaml_dataOverrideReplacesTheHibernationSection() {
        val entry = NpcYamlEntry(hibernation = HibernationConfig(8.0, 3.0))
        val override =
            Yaml.default.decodeFromString(
                NpcYamlOverride.serializer(),
                """
                hibernation:
                  hoursPerCycle: 2.0
                  cycleDays: 1.0
                """
                    .trimIndent())
        val merged = entry.copy(hibernation = override.hibernation ?: entry.hibernation)
        assertEquals(2.0, merged.hibernation?.hoursPerCycle)
        assertEquals(1.0, merged.hibernation?.cycleDays)
    }
}
