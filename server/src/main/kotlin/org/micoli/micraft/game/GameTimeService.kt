package org.micoli.micraft.game

/**
 * How long a game day lasts, and how far into one we are.
 *
 * [gameDayDurationSecondsOf] is a lambda rather than a value because the live server reads it from
 * `data/config/npc.yaml`, which is loaded during `start()` — after the services that need the clock
 * have been constructed. Taking the number eagerly made the configured value silently unreachable,
 * and only the fact that the yaml happened to repeat the code default hid it.
 */
class GameTimeService(private val gameDayDurationSecondsOf: () -> Double) {

    constructor(gameDayDurationSeconds: Double = 1200.0) : this({ gameDayDurationSeconds })

    val gameDayDurationSeconds: Double
        get() = gameDayDurationSecondsOf()

    @Volatile
    var gameTimeSeconds: Double = 0.0
        private set

    val currentGameDay: Double
        get() = gameTimeSeconds / gameDayDurationSeconds

    fun tick(dtSeconds: Double) {
        gameTimeSeconds += dtSeconds
    }

    fun load(savedSeconds: Double) {
        gameTimeSeconds = savedSeconds
    }
}
