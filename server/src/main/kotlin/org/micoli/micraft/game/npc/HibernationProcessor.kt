package org.micoli.micraft.game.npc

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(HibernationProcessor::class.java)

/**
 * Drives the sleep/wake flag of every NPC whose definition carries a [HibernationConfig]. Ticked
 * before the NPC movement and aggro passes, both of which skip a sleeping NPC.
 */
class HibernationProcessor(
    private val npcManager: NpcManager,
    private val gameDay: () -> Double,
) {
    fun tick() {
        val day = gameDay()
        for (instance in npcManager.getAll().toList()) {
            val config = instance.definition.hibernation ?: continue
            if (instance.isDead) {
                instance.hibernating = false
                instance.hibernationWakeForced = false
                continue
            }
            val inWindow = config.isInWindow(day, config.offsetFor(instance.state.id))
            if (!inWindow) {
                // Out of the window the forced wake-up has no more meaning: the next window starts
                // clean and the NPC sleeps again.
                instance.hibernationWakeForced = false
            }
            val shouldSleep = inWindow && !instance.hibernationWakeForced
            if (shouldSleep == instance.hibernating) continue
            instance.hibernating = shouldSleep
            if (shouldSleep) {
                instance.aggroTarget = null
                instance.npcAggroTarget = null
                instance.chaseTargetPos = null
            }
            log.debug(
                "NPC {} {}", instance.state.name, if (shouldSleep) "hibernates" else "wakes up")
        }
    }
}
