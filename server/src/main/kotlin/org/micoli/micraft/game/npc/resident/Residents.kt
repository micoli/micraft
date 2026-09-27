package org.micoli.micraft.game.npc.resident

import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.player.Vec3

/** Settles a Quest giver or merchant spawning at a position as a Resident of that Region. */
class Residents(private val world: WorldState) {

    /**
     * The first rank of [npcType] no living Resident of the Region holds, named apart from every
     * [isTaken] name; null outside Regions.
     */
    fun settle(
        npcType: String,
        pos: Vec3,
        living: Collection<NpcInstance>,
        isTaken: (String) -> Boolean,
    ): Resident? {
        val region = world.regionAt(pos) ?: return null
        val taken =
            living
                .filter { !it.isDead }
                .mapNotNull { it.resident?.key }
                .filter { it.regionKey == region.key && it.npcType == npcType }
                .map { it.rank }
                .toSet()
        val rank = generateSequence(1) { it + 1 }.first { it !in taken }
        return Resident.of(world.worldSeed, ResidentKey(region.key, npcType, rank), isTaken)
    }
}
