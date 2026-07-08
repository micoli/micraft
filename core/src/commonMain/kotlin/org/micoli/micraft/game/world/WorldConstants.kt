package org.micoli.micraft.game.world

object WorldConstants {
    var WORLD_MIN_Y = 0
    var WORLD_MAX_Y = 1024
    var CHUNK_SIZE = 16
    var VIEW_RADIUS = 3
    var FORWARD_VIEW_RADIUS = 7
    val CLIENT_VIEW_RADIUS
        get() = FORWARD_VIEW_RADIUS

    var WATER_LEVEL = 65
}

object PlayerConstants {
    var HEIGHT_STANDING = 1.8f
    var HEIGHT_SNEAKING = 1.5f
    var HEIGHT_CRAWLING = 0.6f
    var WIDTH = 0.6f
    var EYE_OFFSET_STANDING = 1.62f
    var EYE_OFFSET_SNEAKING = 1.27f
    var EYE_OFFSET_CRAWLING = 0.4f
    var SPEED_STANDING = 4.5f
    var SPEED_SNEAKING = 1.3f
    var SPEED_CRAWLING = 1.0f
}
