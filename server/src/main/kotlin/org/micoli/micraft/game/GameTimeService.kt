package org.micoli.micraft.game

class GameTimeService(val gameDayDurationSeconds: Double = 1200.0) {
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
