package org.micoli.micraft.game.world

object WorldConstants {
    const val RPG_LEVEL_MAX = 60
    var WORLD_MIN_Y = 0
    var WORLD_MAX_Y = 1024
    var CHUNK_SIZE = 16
    var VIEW_RADIUS = 3
    var FORWARD_VIEW_RADIUS = 7
    val CLIENT_VIEW_RADIUS
        get() = FORWARD_VIEW_RADIUS

    var WATER_LEVEL = 65

    /**
     * Server-side chunk retention. A chunk is kept in memory while any player is within
     * [FORWARD_VIEW_RADIUS] + [CHUNK_KEEP_MARGIN] chunks of it, and for
     * [CHUNK_UNLOAD_GRACE_SECONDS] afterwards (covers a quick reconnect). Beyond that it is flushed
     * to disk and dropped. Disabled for worlds without persistence.
     */
    var CHUNK_KEEP_MARGIN = 2
    var CHUNK_UNLOAD_GRACE_SECONDS = 120

    /**
     * Fixed depth (blocks) each far-chunk impostor column's perimeter walls extend below its own
     * top height — see buildChunkImpostorMesh (chunkBuilder.ts). Guarantees a fully skirted,
     * gap-free silhouette from any nearby angle without needing per-neighbor height comparisons.
     */
    var IMPOSTOR_SKIRT_DEPTH = 12
}

object PlayerConstants {
    var HEIGHT_STANDING = 2.1f
    var HEIGHT_SNEAKING = 1.5f
    var HEIGHT_CRAWLING = 0.6f
    var WIDTH = 0.6f
    var EYE_OFFSET_STANDING = 1.62f
    var EYE_OFFSET_SNEAKING = 1.27f
    var EYE_OFFSET_CRAWLING = 0.4f
    var SPEED_STANDING = 4.5f
    var SPEED_SNEAKING = 1.3f
    var SPEED_CRAWLING = 1.0f

    /** Vertical speed (blocks/s) when actively swimming up (ascend/jump held while submerged). */
    var SWIM_UP_SPEED = 4f
    /** Vertical speed (blocks/s) when actively diving (descend held while submerged). */
    var SWIM_DOWN_SPEED = 4f
}

/**
 * Breathing / drowning. Values are in ticks; the game loop runs at a fixed tick rate so a duration
 * in seconds is `MAX_BREATH_TICKS / tickRate`. Applies identically to players and to non-aquatic
 * NPCs.
 */
object BreathConstants {
    var MAX_BREATH_TICKS = 300
    var DRAIN_PER_TICK = 1
    var REFILL_PER_TICK = 15
    /** HP removed per damage interval once breath is exhausted. */
    var DAMAGE_PER_INTERVAL = 2
    var DAMAGE_INTERVAL_TICKS = 20
}
