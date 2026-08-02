package org.micoli.micraft.simulation

import kotlin.math.floor
import kotlinx.serialization.Serializable

private fun HashMap<String, Int>.bump(type: String) {
    this[type] = (this[type] ?: 0) + 1
}

private fun sumByType(vararg maps: Map<String, Int>): Map<String, Int> {
    val total = HashMap<String, Int>()
    for (map in maps) for ((type, count) in map) total[type] = (total[type] ?: 0) + count
    return total
}

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
    /** Every death, whatever the cause — the sum of the three maps below. */
    val deathsByType: Map<String, Int> = emptyMap(),
    val ageDeathsByType: Map<String, Int> = emptyMap(),
    val killDeathsByType: Map<String, Int> = emptyMap(),
    val starvationsByType: Map<String, Int> = emptyMap(),
    val birthsByType: Map<String, Int> = emptyMap(),
    val evolutionsByType: Map<String, Int> = emptyMap(),
    val aliveByType: Map<String, Int> = emptyMap(),
    /**
     * Condition of the standing population, per type — gauges, like [aliveByType].
     *
     * These are what turn "the population fell" into a reason. Hunger pegged at 1.0 across a whole
     * species says the food base is gone; an adult share near zero says nothing is growing up. Both
     * were true in the 60-day run and neither was visible on any chart.
     */
    val meanHungerByType: Map<String, Double> = emptyMap(),
    /** Mean age as a fraction of the type's lifespan; 0 when it has none. */
    val meanAgeRatioByType: Map<String, Double> = emptyMap(),
    /** Share of the type that is adult (has no `adultType` to grow into). */
    val adultShareByType: Map<String, Double> = emptyMap(),
    val starvingShareByType: Map<String, Double> = emptyMap(),
    val pregnantShareByType: Map<String, Double> = emptyMap(),
    val attacks: Int = 0,
    val gestations: Int = 0,
    val births: Int = 0,
    val birthsBlocked: Int = 0,
    val matings: Int = 0,
    val spawns: Int = 0,
    val fed: Int = 0,
    val hungry: Int = 0,
    val evolutions: Int = 0,
)

/**
 * The standing population and its condition, sampled together.
 *
 * One object because it comes from one pass over the NPCs: counting a few thousand of them five
 * times to fill five maps would cost more than the whole metric is worth.
 */
class PopulationSample(
    val aliveByType: Map<String, Int> = emptyMap(),
    val meanHungerByType: Map<String, Double> = emptyMap(),
    val meanAgeRatioByType: Map<String, Double> = emptyMap(),
    val adultShareByType: Map<String, Double> = emptyMap(),
    val starvingShareByType: Map<String, Double> = emptyMap(),
    val pregnantShareByType: Map<String, Double> = emptyMap(),
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
        val ageDeaths = HashMap<String, Int>()
        val killDeaths = HashMap<String, Int>()
        val starvations = HashMap<String, Int>()
        val birthsPerType = HashMap<String, Int>()
        val evolutionsPerType = HashMap<String, Int>()
        var population: PopulationSample = PopulationSample()
        var attacks = 0
        var gestations = 0
        var births = 0
        var birthsBlocked = 0
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
                deathsByType = sumByType(ageDeaths, killDeaths, starvations),
                ageDeathsByType = ageDeaths.toMap(),
                killDeathsByType = killDeaths.toMap(),
                starvationsByType = starvations.toMap(),
                birthsByType = birthsPerType.toMap(),
                evolutionsByType = evolutionsPerType.toMap(),
                aliveByType = population.aliveByType,
                meanHungerByType = population.meanHungerByType,
                meanAgeRatioByType = population.meanAgeRatioByType,
                adultShareByType = population.adultShareByType,
                starvingShareByType = population.starvingShareByType,
                pregnantShareByType = population.pregnantShareByType,
                attacks = attacks,
                gestations = gestations,
                births = births,
                birthsBlocked = birthsBlocked,
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
        val npcType = event.npcType ?: UNKNOWN_TYPE
        when (event.type) {
            SimEventType.DEATH -> slice.killDeaths.bump(npcType)
            SimEventType.AGE_DEATH -> slice.ageDeaths.bump(npcType)
            SimEventType.STARVATION -> slice.starvations.bump(npcType)
            SimEventType.ATTACK -> slice.attacks++
            SimEventType.GESTATION_START -> slice.gestations++
            SimEventType.BIRTH -> {
                slice.births++
                slice.birthsPerType.bump(npcType)
            }
            SimEventType.BIRTH_BLOCKED -> slice.birthsBlocked++
            SimEventType.MATING -> slice.matings++
            SimEventType.SPAWN -> slice.spawns++
            SimEventType.FED -> slice.fed++
            SimEventType.HUNGRY -> slice.hungry++
            SimEventType.EVOLVE -> {
                slice.evolutions++
                slice.evolutionsPerType.bump(npcType)
            }
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
        population: () -> PopulationSample,
    ) {
        val known = slices.lastOrNull()?.index
        val slice = sliceFor(gameDay, tick) ?: return
        val opened = slice.index != known
        if (!opened && nowMs - lastSampleMs < SAMPLE_MIN_MS) return
        lastSampleMs = nowMs
        slice.population = population()
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
        slice.population = last?.population ?: PopulationSample()
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
