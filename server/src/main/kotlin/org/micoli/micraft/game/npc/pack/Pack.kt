package org.micoli.micraft.game.npc.pack

/** A hunt in progress: the NPCs that answered the call, and what they are after. */
class Pack(
    val id: String,
    val initiatorId: String,
    val targetId: String,
    /** Rules of the NPC that called the hunt; they govern the whole pack. */
    val config: PackConfig,
    val memberIds: MutableSet<String>,
    val createdAtMs: Long,
    @Volatile var engaged: Boolean = false,
)

/**
 * Notification emitted by [PackCoordinator]. The live server ignores these (default no-op sink);
 * the admin world simulator turns them into its event log.
 */
data class PackEvent(
    val type: PackEventType,
    val packId: String,
    val npcId: String,
    val npcName: String,
    val npcType: String,
    val otherId: String? = null,
    val otherName: String? = null,
    /** Contextual number: pack size at the time of the event. */
    val value: Double? = null,
)

enum class PackEventType {
    PACK_CALL,
    PACK_JOIN,
    PACK_ENGAGE,
    PACK_DISBAND,
}
