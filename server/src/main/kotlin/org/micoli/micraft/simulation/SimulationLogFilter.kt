package org.micoli.micraft.simulation

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply

/** Threads the world simulator ticks on; see [isSimulationLogEvent]. */
const val SIMULATION_THREAD_PREFIX = "simulation-worker"

private const val SIMULATION_PACKAGE = "org.micoli.micraft.simulation"

/**
 * Does this log event come from a world simulation rather than from the live server?
 *
 * Two rules, because logger names alone are not enough. A fast arena drives the very same game
 * systems as the live world ([org.micoli.micraft.game.npc.NpcManager],
 * `AnimalInteractionProcessor`, `VegetationManager`…) and those log per spawn, per death, per
 * regrowth — at a few thousand ticks a second that buries the real server log. So: anything logged
 * **from a simulation worker thread** (catches the shared game classes) and anything logged **by
 * the simulation package** (catches the registry, wherever it is called from).
 */
fun isSimulationLogEvent(event: ILoggingEvent): Boolean =
    event.threadName?.startsWith(SIMULATION_THREAD_PREFIX) == true ||
        event.loggerName?.startsWith(SIMULATION_PACKAGE) == true

/** Keeps simulation chatter out of the server log. Used on the server appenders. */
class SimulationLogFilter : Filter<ILoggingEvent>() {
    override fun decide(event: ILoggingEvent): FilterReply =
        if (isSimulationLogEvent(event)) FilterReply.DENY else FilterReply.NEUTRAL
}

/** Keeps everything but simulation chatter out. Used on the simulation appender. */
class SimulationOnlyLogFilter : Filter<ILoggingEvent>() {
    override fun decide(event: ILoggingEvent): FilterReply =
        if (isSimulationLogEvent(event)) FilterReply.NEUTRAL else FilterReply.DENY
}
