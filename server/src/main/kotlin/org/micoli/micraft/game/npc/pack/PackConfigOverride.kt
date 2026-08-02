package org.micoli.micraft.game.npc.pack

import kotlinx.serialization.Serializable

/**
 * Per-field override of a [PackConfig]. Nullable throughout for the same reason as
 * `AnimalYamlOverride`: raising `minSizeToEngage` alone must not wipe `hostileTypes`, which would
 * quietly stop the species pack-hunting altogether.
 */
@Serializable
data class PackConfigOverride(
    val extendPackType: List<String>? = null,
    val callRadius: Float? = null,
    val relayHops: Int? = null,
    val maxSize: Int? = null,
    val minSizeToEngage: Int? = null,
    val callCooldownSec: Float? = null,
    val rallyTimeoutSec: Float? = null,
    val chaseRadius: Float? = null,
    val hostileTypes: List<String>? = null,
)

fun PackConfig.applyOverride(o: PackConfigOverride) =
    copy(
        extendPackType = o.extendPackType ?: extendPackType,
        callRadius = o.callRadius ?: callRadius,
        relayHops = o.relayHops ?: relayHops,
        maxSize = o.maxSize ?: maxSize,
        minSizeToEngage = o.minSizeToEngage ?: minSizeToEngage,
        callCooldownSec = o.callCooldownSec ?: callCooldownSec,
        rallyTimeoutSec = o.rallyTimeoutSec ?: rallyTimeoutSec,
        chaseRadius = o.chaseRadius ?: chaseRadius,
        hostileTypes = o.hostileTypes ?: hostileTypes,
    )

/** An override applied to a type that has no `pack:` block yet. */
fun PackConfigOverride.toConfig() = PackConfig().applyOverride(this)
