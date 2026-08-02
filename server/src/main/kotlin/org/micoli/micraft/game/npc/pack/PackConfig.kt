package org.micoli.micraft.game.npc.pack

import kotlinx.serialization.Serializable

/**
 * Pack-hunting rules for a species, declared under `pack:` in the entity YAML.
 *
 * A lone wolf cannot bring down a polar bear, so wolves call each other from neighbour to neighbour
 * and only engage once enough of them have gathered.
 */
@Serializable
data class PackConfig(
    /**
     * Allied types *in addition to* the caller's own type, which is always implicitly included.
     * Keep the lists symmetric between allied species, otherwise a recruit cannot relay the call to
     * its own kin.
     */
    val extendPackType: List<String> = emptyList(),
    /** How far a single relay of the call carries, in blocks. */
    val callRadius: Float = 25.0f,
    /** Number of neighbour-to-neighbour relays the call is allowed to make. */
    val relayHops: Int = 3,
    val maxSize: Int = 6,
    /** Members that must be within reach of the target before the pack engages. */
    val minSizeToEngage: Int = 3,
    /** Silence imposed on a member after its pack disbands. */
    val callCooldownSec: Float = 20.0f,
    /** A pack that never reaches its quorum within this delay gives up. */
    val rallyTimeoutSec: Float = 20.0f,
    /** Leash radius around the spawn point while hunting in a pack, replaces `aggroRange`. */
    val chaseRadius: Float = 45.0f,
    /** NPC types this species gangs up on. */
    val hostileTypes: List<String> = emptyList(),
) {
    /** Types that may join a pack led through this member. */
    fun alliedTypes(ownType: String): Set<String> = extendPackType.toSet() + ownType
}
