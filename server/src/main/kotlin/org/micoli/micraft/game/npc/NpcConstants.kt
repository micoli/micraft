package org.micoli.micraft.game.npc

/**
 * Holder for the tunables of the live server world, fed from `data/config/npc.yaml` by
 * [NpcConfigLoader]. Other world instances (the admin world simulator) carry their own [NpcTuning]
 * instead of reading this.
 */
object NpcConstants {
    @Volatile var live: NpcTuning = NpcTuning()
}
