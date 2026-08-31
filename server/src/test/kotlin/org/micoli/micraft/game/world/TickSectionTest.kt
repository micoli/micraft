package org.micoli.micraft.game.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TickSectionTest {

    @Test
    fun realtime_isEverySection() {
        assertEquals(TickSection.entries.toSet(), TickSection.REALTIME)
    }

    @Test
    fun e2e_skipsExactlyTheOldFullSimulationTick() {
        // Pre-TickSection, an `e2eCreative` world ran gameTicks + time-broadcast + the player pass
        // +
        // world-item collection + plugins, and skipped all of fullSimulationTick().
        val fullSimulationTick =
            setOf(
                TickSection.NPC,
                TickSection.NPC_LIFECYCLE,
                TickSection.VEHICLES,
                TickSection.SIEGE,
                TickSection.STATUS_EFFECTS,
                TickSection.REGEN,
                TickSection.WEATHER,
                TickSection.LIQUID,
                TickSection.VEGETATION,
                TickSection.AUCTION,
                TickSection.TARGET_DISTANCE,
            )
        assertEquals(
            setOf(
                TickSection.TIME_BROADCAST,
                TickSection.PLAYERS,
                TickSection.WORLD_ITEMS,
                TickSection.PLUGINS,
            ),
            TickSection.E2E,
        )
        assertTrue(TickSection.E2E.none { it in fullSimulationTick })
    }

    @Test
    fun simulation_carriesTheNpcEcologyAndWhatFeedsIt() {
        assertTrue(TickSection.NPC in TickSection.SIMULATION)
        assertTrue(TickSection.NPC_LIFECYCLE in TickSection.SIMULATION)
        assertTrue(TickSection.VEGETATION in TickSection.SIMULATION)
        assertTrue(TickSection.PLAYERS in TickSection.SIMULATION)
        assertTrue(TickSection.WEATHER !in TickSection.SIMULATION)
        assertTrue(TickSection.LIQUID !in TickSection.SIMULATION)
    }
}
