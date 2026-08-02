package org.micoli.micraft.simulation

import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.serialization.Serializable

enum class SimEventType {
    SPAWN,
    DESPAWN,
    ATTACK,
    DAMAGE,
    DEATH,
    AGE_DEATH,
    AGGRO_GAIN,
    AGGRO_LOST,
    HUNGRY,
    FED,
    MATING,
    GESTATION_START,
    BIRTH,
    EVOLVE,
    PACK_CALL,
    PACK_JOIN,
    PACK_ENGAGE,
    PACK_DISBAND,
    SYSTEM,
}

@Serializable
data class SimEvent(
    val seq: Long,
    val tick: Long,
    val gameDay: Double,
    val type: SimEventType,
    val message: String,
    val npcId: String? = null,
    val npcName: String? = null,
    val npcType: String? = null,
    val otherId: String? = null,
    val otherName: String? = null,
    val value: Double? = null,
)

/**
 * Fixed-size history of what happened in the arena. Oldest entries are dropped once [capacity] is
 * reached, so a fast simulation cannot grow without bound.
 */
class SimEventLog(val capacity: Int = 300) {
    private val events = ConcurrentLinkedDeque<SimEvent>()
    private var seq = 0L

    val size: Int
        get() = events.size

    @Synchronized
    fun add(event: SimEvent): SimEvent {
        val stamped = event.copy(seq = ++seq)
        events.addLast(stamped)
        while (events.size > capacity) events.pollFirst()
        return stamped
    }

    fun snapshot(): List<SimEvent> = events.toList()

    /** Events newer than [afterSeq], oldest first. */
    fun since(afterSeq: Long): List<SimEvent> = events.filter { it.seq > afterSeq }

    @Synchronized
    fun clear() {
        events.clear()
        seq = 0L
    }
}
