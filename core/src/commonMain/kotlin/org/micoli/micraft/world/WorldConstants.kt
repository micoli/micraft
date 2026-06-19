package org.micoli.micraft.world

object WorldConstants {
    const val WORLD_MIN_Y = 0
    const val WORLD_MAX_Y = 1024
    const val CHUNK_SIZE = 16
}

object PlayerConstants {
    const val HEIGHT_STANDING = 1.8f
    const val HEIGHT_SNEAKING = 1.5f
    const val HEIGHT_CRAWLING = 0.6f
    const val WIDTH = 0.6f
    const val EYE_OFFSET_STANDING = 1.62f
    const val EYE_OFFSET_SNEAKING = 1.27f
    const val EYE_OFFSET_CRAWLING = 0.4f
    const val SPEED_STANDING = 4.5f   // blocks/s
    const val SPEED_SNEAKING = 1.3f
    const val SPEED_CRAWLING = 1.0f
}
