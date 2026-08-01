package org.micoli.micraft.simulation

import kotlin.math.floor
import kotlinx.serialization.Serializable

/**
 * One slice of arena history.
 *
 * Counters ([deaths], [attacks], …) are sums over the slice; [aliveByType] is a *gauge* — the
 * population as last sampled inside the slice, not a total. Mixing the two in one bucket is
 * deliberate: a chart of "how many died" only reads correctly next to "how many were there".
 */
@Serializable
data class SimMetricBucket(
    val index: Long,
    val startGameDay: Double,
    val tick: Long,
    val deathsByType: Map<String, Int> = emptyMap(),
    val aliveByType: Map<String, Int> = emptyMap(),
    val attacks: Int = 0,
    val gestations: Int = 0,
    val births: Int = 0,
    val matings: Int = 0,
    val spawns: Int = 0,
    val fed: Int = 0,
    val hungry: Int = 0,
    val evolutions: Int = 0,
)

@Serializable
data class SimMetricsDto(
    val bucketGameDays: Double,
    val buckets: List<SimMetricBucket>,
)

/**
 * Rolling time series of what the arena did, bucketed by **game time**.
 *
 * Game time, not wall-clock: the same arena runs at 200 or 5000 ticks a second, and a chart whose x
 * axis is wall-clock would stretch and squash as the operator moves the speed slider, making two
 * runs impossible to compare. One bucket is always the same amount of simulated life.
 *
 * History is capped at [capacity] buckets so a fast arena left running overnight cannot grow
 * without bound — the same reason [SimEventLog] is capped.
 */
class SimMetrics(
    val bucketGameDays: Double = DEFAULT_BUCKET_GAME_DAYS,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private class Slice(val index: Long, val startGameDay: Double) {
        var tick = 0L
        val deaths = HashMap<String, Int>()
        var alive: Map<String, Int> = emptyMap()
        var attacks = 0
        var gestations = 0
        var births = 0
        var matings = 0
        var spawns = 0
        var fed = 0
        var hungry = 0
        var evolutions = 0

        fun toDto() =
            SimMetricBucket(
                index = index,
                startGameDay = startGameDay,
                tick = tick,
                deathsByType = deaths.toMap(),
                aliveByType = alive,
                attacks = attacks,
                gestations = gestations,
                births = births,
                matings = matings,
                spawns = spawns,
                fed = fed,
                hungry = hungry,
                evolutions = evolutions,
            )
    }

    private val slices = ArrayDeque<Slice>()
    private var lastSampleMs = 0L

    val size: Int
        @Synchronized get() = slices.size

    /** Fold one event into its slice. Event types with no series of their own are ignored. */
    @Synchronized
    fun record(event: SimEvent) {
        val slice = sliceFor(event.gameDay, event.tick) ?: return
        when (event.type) {
            SimEventType.DEATH,
            SimEventType.AGE_DEATH -> {
                val type = event.npcType ?: UNKNOWN_TYPE
                slice.deaths[type] = (slice.deaths[type] ?: 0) + 1
            }
            SimEventType.ATTACK -> slice.attacks++
            SimEventType.GESTATION_START -> slice.gestations++
            SimEventType.BIRTH -> slice.births++
            SimEventType.MATING -> slice.matings++
            SimEventType.SPAWN -> slice.spawns++
            SimEventType.FED -> slice.fed++
            SimEventType.HUNGRY -> slice.hungry++
            SimEventType.EVOLVE -> slice.evolutions++
            else -> Unit
        }
    }

    /**
     * Record the standing population. [aliveByType] is a lambda because counting a few thousand
     * NPCs every tick would cost more than the whole metric is worth: it is only called when a new
     * slice opens, or when the open slice's sample is older than [SAMPLE_MIN_MS] — so the newest
     * point on the chart keeps moving without paying per tick.
     */
    @Synchronized
    fun sample(
        gameDay: Double,
        tick: Long,
        nowMs: Long = System.currentTimeMillis(),
        aliveByType: () -> Map<String, Int>,
    ) {
        val known = slices.lastOrNull()?.index
        val slice = sliceFor(gameDay, tick) ?: return
        val opened = slice.index != known
        if (!opened && nowMs - lastSampleMs < SAMPLE_MIN_MS) return
        lastSampleMs = nowMs
        slice.alive = aliveByType()
    }

    @Synchronized fun snapshot(): List<SimMetricBucket> = slices.map { it.toDto() }

    /**
     * Buckets from [fromIndex] on, **inclusive** — the caller's last bucket was still open when it
     * was sent, so it has to be re-sent to pick up what landed in it since.
     */
    @Synchronized
    fun since(fromIndex: Long): List<SimMetricBucket> =
        slices.filter { it.index >= fromIndex }.map { it.toDto() }

    @Synchronized
    fun clear() {
        slices.clear()
        lastSampleMs = 0L
    }

    /**
     * The slice holding [gameDay], opening it if needed. Returns null for a game day older than the
     * retained window: history has already been trimmed there and re-opening the slice would put an
     * out-of-order bucket in the middle of the series.
     */
    private fun sliceFor(gameDay: Double, tick: Long): Slice? {
        val index = floor(gameDay / bucketGameDays).toLong()
        val last = slices.lastOrNull()
        if (last != null && index == last.index) {
            last.tick = tick
            return last
        }
        if (last != null && index < last.index) return slices.find { it.index == index }
        val slice = Slice(index, index * bucketGameDays).also { it.tick = tick }
        // a new slice inherits the population: nothing died just because a bucket rolled over
        slice.alive = last?.alive ?: emptyMap()
        slices.addLast(slice)
        while (slices.size > capacity) slices.removeFirst()
        return slice
    }

    companion object {
        /**
         * Quarter of a game day: fine enough to see a hunger cycle, coarse enough to stay cheap.
         */
        const val DEFAULT_BUCKET_GAME_DAYS = 0.25

        /** 240 × 0.25 = 60 game days of history. */
        const val DEFAULT_CAPACITY = 240

        private const val SAMPLE_MIN_MS = 250L
        private const val UNKNOWN_TYPE = "inconnu"
    }
}
