package org.micoli.micraft.game.npc.animal

/**
 * Lifecycle notification emitted by [AnimalInteractionProcessor]. The live server ignores these
 * (default no-op sink); the admin world simulator turns them into its event log so hunger, mating,
 * gestation and birth can be watched in real time.
 */
data class AnimalEvent(
    val type: AnimalEventType,
    val npcId: String,
    val npcName: String,
    val npcType: String,
    val otherId: String? = null,
    val otherName: String? = null,
    /** Contextual number: hunger ratio, gestation days left, age in game days, offspring count. */
    val value: Double? = null,
)

enum class AnimalEventType {
    HUNGRY,
    FED,
    MATING,
    GESTATION_START,
    BIRTH,
    EVOLVE,
    AGE_DEATH,
}
