package org.micoli.micraft.game.world.actionblock

object ActionBlockConstants {
    const val MAX_NAME_LENGTH = 48
    const val MAX_SCRIPT_LENGTH = 4096
    const val MAX_VARIABLES = 64
    const val MAX_VARIABLE_KEY_LENGTH = 48
    const val MAX_VARIABLE_VALUE_LENGTH = 512

    /** `remote()` call-depth ceiling before the engine aborts the chain. */
    const val MAX_REMOTE_DEPTH = 8

    /** Total script runs allowed for one interaction (guards fan-out loops). */
    const val MAX_SCRIPT_RUNS_PER_INTERACTION = 32

    /** Star billboard height above the block centre, in blocks. */
    const val STAR_ICON_HEIGHT = 1.4f

    const val DEFAULT_NAME_PREFIX = "actionblock-"
}
