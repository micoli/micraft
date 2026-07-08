package org.micoli.micraft.game.trade

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.micoli.micraft.game.world.ItemType

class PendingTrade(
    val id: String,
    val initiatorId: String,
    val targetId: String,
) {
    val initiatorOffer = ConcurrentHashMap<ItemType, Int>()
    val targetOffer = ConcurrentHashMap<ItemType, Int>()
    @Volatile var initiatorAccepted = false
    @Volatile var targetAccepted = false
    /** Guards against double-execution/double-cancel. */
    val closed = AtomicBoolean(false)
}
