package org.micoli.micraft.player

import kotlinx.serialization.Serializable
import org.micoli.micraft.world.PlayerConstants

@Serializable
enum class PlayerStance {
    STANDING,
    SNEAKING,
    CRAWLING
}

val PlayerStance.height: Float
    get() =
        when (this) {
            PlayerStance.STANDING -> PlayerConstants.HEIGHT_STANDING
            PlayerStance.SNEAKING -> PlayerConstants.HEIGHT_SNEAKING
            PlayerStance.CRAWLING -> PlayerConstants.HEIGHT_CRAWLING
        }

val PlayerStance.eyeOffset: Float
    get() =
        when (this) {
            PlayerStance.STANDING -> PlayerConstants.EYE_OFFSET_STANDING
            PlayerStance.SNEAKING -> PlayerConstants.EYE_OFFSET_SNEAKING
            PlayerStance.CRAWLING -> PlayerConstants.EYE_OFFSET_CRAWLING
        }

val PlayerStance.speed: Float
    get() =
        when (this) {
            PlayerStance.STANDING -> PlayerConstants.SPEED_STANDING
            PlayerStance.SNEAKING -> PlayerConstants.SPEED_SNEAKING
            PlayerStance.CRAWLING -> PlayerConstants.SPEED_CRAWLING
        }
