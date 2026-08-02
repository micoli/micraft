package org.micoli.micraft.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun event(
    type: SimEventType,
    gameDay: Double,
    npcType: String? = null,
    tick: Long = 1L,
) = SimEvent(seq = 1L, tick = tick, gameDay = gameDay, type = type, message = "", npcType = npcType)

class SimMetricsTest {

    @Test
    fun eventsLandInTheBucketOfTheirGameDay() {
        val metrics = SimMetrics(bucketGameDays = 0.25)
        metrics.record(event(SimEventType.ATTACK, 0.1))
        metrics.record(event(SimEventType.ATTACK, 0.2))
        metrics.record(event(SimEventType.ATTACK, 0.3))

        val buckets = metrics.snapshot()
        assertEquals(2, buckets.size)
        assertEquals(2, buckets[0].attacks)
        assertEquals(1, buckets[1].attacks)
        assertEquals(0.0, buckets[0].startGameDay)
        assertEquals(0.25, buckets[1].startGameDay)
    }

    @Test
    fun deathsAreSplitPerNpcType() {
        val metrics = SimMetrics(bucketGameDays = 1.0)
        metrics.record(event(SimEventType.DEATH, 0.1, npcType = "goat"))
        metrics.record(event(SimEventType.AGE_DEATH, 0.5, npcType = "goat"))
        metrics.record(event(SimEventType.DEATH, 0.7, npcType = "wolf"))

        val bucket = metrics.snapshot().single()
        assertEquals(mapOf("goat" to 2, "wolf" to 1), bucket.deathsByType)
    }

    /**
     * Old age, predation and starvation are opposite balance problems: a single `deathsByType`
     * series cannot tell "the wolves are eating everything" from "everything is dying of old age".
     */
    @Test
    fun deathsAreAlsoSplitPerCause() {
        val metrics = SimMetrics(bucketGameDays = 1.0)
        metrics.record(event(SimEventType.DEATH, 0.1, npcType = "goat"))
        metrics.record(event(SimEventType.AGE_DEATH, 0.2, npcType = "goat"))
        metrics.record(event(SimEventType.AGE_DEATH, 0.3, npcType = "wolf"))
        metrics.record(event(SimEventType.STARVATION, 0.4, npcType = "wolf"))

        val bucket = metrics.snapshot().single()
        assertEquals(mapOf("goat" to 1), bucket.killDeathsByType)
        assertEquals(mapOf("goat" to 1, "wolf" to 1), bucket.ageDeathsByType)
        assertEquals(mapOf("wolf" to 1), bucket.starvationsByType)
    }

    @Test
    fun deathsByType_isTheSumOfTheThreeCauses() {
        val metrics = SimMetrics(bucketGameDays = 1.0)
        metrics.record(event(SimEventType.DEATH, 0.1, npcType = "goat"))
        metrics.record(event(SimEventType.AGE_DEATH, 0.2, npcType = "goat"))
        metrics.record(event(SimEventType.STARVATION, 0.3, npcType = "goat"))

        val bucket = metrics.snapshot().single()
        val perCause =
            bucket.ageDeathsByType.values.sum() +
                bucket.killDeathsByType.values.sum() +
                bucket.starvationsByType.values.sum()
        assertEquals(perCause, bucket.deathsByType.values.sum())
        assertEquals(mapOf("goat" to 3), bucket.deathsByType)
    }

    @Test
    fun blockedBirthsHaveTheirOwnCounter() {
        val metrics = SimMetrics(bucketGameDays = 1.0)
        metrics.record(event(SimEventType.BIRTH, 0.1, npcType = "goat"))
        metrics.record(event(SimEventType.BIRTH_BLOCKED, 0.2, npcType = "goat"))
        metrics.record(event(SimEventType.BIRTH_BLOCKED, 0.3, npcType = "goat"))

        val bucket = metrics.snapshot().single()
        assertEquals(1, bucket.births)
        // a birth refused by the population ceiling must never read as a birth
        assertEquals(2, bucket.birthsBlocked)
        assertTrue(bucket.deathsByType.isEmpty())
    }

    @Test
    fun aDeathWithNoKnownType_isStillCounted() {
        val metrics = SimMetrics(bucketGameDays = 1.0)
        metrics.record(event(SimEventType.DEATH, 0.1, npcType = null))
        // losing the row entirely would make the chart quietly under-report
        assertEquals(1, metrics.snapshot().single().deathsByType.values.sum())
    }

    @Test
    fun eachCounterHasItsOwnSeries() {
        val metrics = SimMetrics(bucketGameDays = 1.0)
        listOf(
                SimEventType.GESTATION_START,
                SimEventType.BIRTH,
                SimEventType.MATING,
                SimEventType.SPAWN,
                SimEventType.FED,
                SimEventType.HUNGRY,
                SimEventType.EVOLVE,
            )
            .forEach { metrics.record(event(it, 0.5)) }

        val bucket = metrics.snapshot().single()
        assertEquals(1, bucket.gestations)
        assertEquals(1, bucket.births)
        assertEquals(1, bucket.matings)
        assertEquals(1, bucket.spawns)
        assertEquals(1, bucket.fed)
        assertEquals(1, bucket.hungry)
        assertEquals(1, bucket.evolutions)
    }

    @Test
    fun untrackedEventTypes_leaveEveryCounterAtZero() {
        val metrics = SimMetrics(bucketGameDays = 1.0)
        metrics.record(event(SimEventType.DAMAGE, 0.1))
        metrics.record(event(SimEventType.AGGRO_GAIN, 0.2))
        metrics.record(event(SimEventType.SYSTEM, 0.3))

        val bucket = metrics.snapshot().single()
        assertEquals(0, bucket.attacks + bucket.births + bucket.matings)
        assertTrue(bucket.deathsByType.isEmpty())
    }

    @Test
    fun populationIsSampledOnceWhenABucketOpens() {
        val metrics = SimMetrics(bucketGameDays = 0.25)
        var calls = 0
        val alive = {
            calls++
            PopulationSample(aliveByType = mapOf("goat" to 3))
        }

        // same bucket, same instant: sampling every tick would cost a full population scan per tick
        metrics.sample(0.10, tick = 1, nowMs = 1_000L, population = alive)
        metrics.sample(0.11, tick = 2, nowMs = 1_000L, population = alive)
        metrics.sample(0.12, tick = 3, nowMs = 1_010L, population = alive)
        assertEquals(1, calls)

        // new bucket: sampled again whatever the clock says
        metrics.sample(0.30, tick = 4, nowMs = 1_010L, population = alive)
        assertEquals(2, calls)
    }

    @Test
    fun theOpenBucketKeepsRefreshing_soTheChartHeadMoves() {
        val metrics = SimMetrics(bucketGameDays = 1.0)
        metrics.sample(0.1, tick = 1, nowMs = 0L) {
            PopulationSample(aliveByType = mapOf("goat" to 2))
        }
        metrics.sample(0.2, tick = 2, nowMs = 5_000L) {
            PopulationSample(aliveByType = mapOf("goat" to 9))
        }
        assertEquals(mapOf("goat" to 9), metrics.snapshot().single().aliveByType)
    }

    @Test
    fun aNewBucketInheritsThePopulation() {
        val metrics = SimMetrics(bucketGameDays = 0.25)
        metrics.sample(0.1, tick = 1, nowMs = 0L) {
            PopulationSample(aliveByType = mapOf("goat" to 4))
        }
        metrics.record(event(SimEventType.ATTACK, 0.4))
        // nothing died just because a bucket rolled over: a zero here would draw a gap in the chart
        assertEquals(mapOf("goat" to 4), metrics.snapshot().last().aliveByType)
    }

    @Test
    fun historyIsCapped_keepingTheRecentBuckets() {
        val metrics = SimMetrics(bucketGameDays = 1.0, capacity = 3)
        repeat(6) { day -> metrics.record(event(SimEventType.ATTACK, day + 0.5)) }

        val buckets = metrics.snapshot()
        assertEquals(3, buckets.size)
        assertEquals(listOf(3L, 4L, 5L), buckets.map { it.index })
    }

    @Test
    fun anEventOlderThanTheWindow_isDroppedRatherThanReordering() {
        val metrics = SimMetrics(bucketGameDays = 1.0, capacity = 2)
        repeat(4) { day -> metrics.record(event(SimEventType.ATTACK, day + 0.5)) }
        metrics.record(event(SimEventType.ATTACK, 0.5))

        val buckets = metrics.snapshot()
        // out-of-order buckets would make the chart draw backwards
        assertEquals(listOf(2L, 3L), buckets.map { it.index })
    }

    @Test
    fun sinceResendsTheOpenBucket() {
        val metrics = SimMetrics(bucketGameDays = 1.0)
        metrics.record(event(SimEventType.ATTACK, 0.5))
        metrics.record(event(SimEventType.ATTACK, 1.5))

        val sent = metrics.since(0L)
        val last = sent.last().index
        metrics.record(event(SimEventType.ATTACK, 1.7))

        // inclusive lower bound: the bucket was still open when it was sent, so its new count
        // reaches the client instead of being lost
        val resent = metrics.since(last)
        assertEquals(listOf(1L), resent.map { it.index })
        assertEquals(2, resent.single().attacks)
    }

    @Test
    fun clearEmptiesTheSeries() {
        val metrics = SimMetrics()
        metrics.record(event(SimEventType.ATTACK, 0.5))
        metrics.clear()
        assertEquals(0, metrics.size)
    }
}
