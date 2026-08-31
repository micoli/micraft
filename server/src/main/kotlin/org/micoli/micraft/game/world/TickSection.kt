package org.micoli.micraft.game.world

/**
 * A slice of [GameWorld.tick] that a host can turn off. The default world and browser-E2E worlds
 * run every section; the admin world simulator runs only the NPC ecology and what feeds it, so a
 * high-speed arena is not paying for weather, liquids or player-facing broadcasts it never shows.
 *
 * `gameTicks`, the game clock and the plugin gate below stay unconditional — they are the frame,
 * not a section.
 */
enum class TickSection {
    TIME_BROADCAST,
    PLAYERS,
    WORLD_ITEMS,
    NPC,
    NPC_LIFECYCLE,
    VEHICLES,
    SIEGE,
    STATUS_EFFECTS,
    REGEN,
    WEATHER,
    LIQUID,
    VEGETATION,
    AUCTION,
    TARGET_DISTANCE,
    PLUGINS;

    companion object {
        val ALL: Set<TickSection> = entries.toSet()

        /** Default world: everything. */
        val REALTIME: Set<TickSection> = ALL

        /**
         * Browser-E2E worlds: bounded flat generator, `maxNpcs=0`, no vegetation. Only the sections
         * a static test client observes — matching the pre-[TickSection] `e2eCreative` fast path.
         */
        val E2E: Set<TickSection> = setOf(TIME_BROADCAST, PLAYERS, WORLD_ITEMS, PLUGINS)

        /**
         * Admin world simulator: the NPC ecology, the clock that drives ageing, and the player
         * movement pass that triggers zone spawns. No weather/liquid/status-effects/auction/siege.
         */
        val SIMULATION: Set<TickSection> = setOf(PLAYERS, NPC, NPC_LIFECYCLE, VEGETATION)
    }
}
