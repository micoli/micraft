package org.micoli.micraft.simulation

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.spi.FilterReply
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Build a log event as if it were emitted from [threadName]. Logback captures the thread name of
 * whoever creates the event, so the event is created on a thread bearing that name.
 */
private fun event(loggerName: String, threadName: String): LoggingEvent {
    lateinit var built: LoggingEvent
    val thread =
        Thread(
            {
                built =
                    LoggingEvent().apply {
                        this.loggerName = loggerName
                        level = Level.INFO
                        message = "test"
                    }
                // reading it here is what pins the thread name onto the event
                built.threadName
            },
            threadName)
    thread.start()
    thread.join()
    return built
}

class SimulationLogFilterTest {

    private val serverAppender = SimulationLogFilter()
    private val simulationAppender = SimulationOnlyLogFilter()

    @Test
    fun sharedGameClassesTickingInASimulation_areKeptOutOfTheServerLog() {
        // NpcManager is the live world's class too: only the thread tells them apart
        val simulated =
            event("org.micoli.micraft.game.npc.NpcManager", "$SIMULATION_THREAD_PREFIX-1")
        assertEquals(FilterReply.DENY, serverAppender.decide(simulated))
        assertEquals(FilterReply.NEUTRAL, simulationAppender.decide(simulated))
    }

    @Test
    fun theSameClassServingTheLiveWorld_staysInTheServerLog() {
        val live = event("org.micoli.micraft.game.npc.NpcManager", "eventLoopGroupProxy-4")
        assertEquals(FilterReply.NEUTRAL, serverAppender.decide(live))
        assertEquals(FilterReply.DENY, simulationAppender.decide(live))
    }

    @Test
    fun simulationPackage_isRoutedWhateverTheThread() {
        // the registry logs from the websocket thread
        val registry = event("org.micoli.micraft.simulation.SimulationRegistry", "ktor-worker-2")
        assertEquals(FilterReply.DENY, serverAppender.decide(registry))
        assertEquals(FilterReply.NEUTRAL, simulationAppender.decide(registry))
    }

    @Test
    fun unrelatedServerLogging_isUntouched() {
        val ktor = event("io.ktor.server.Application", "main")
        assertEquals(FilterReply.NEUTRAL, serverAppender.decide(ktor))
        assertEquals(FilterReply.DENY, simulationAppender.decide(ktor))
    }

    @Test
    fun theTwoFiltersAreExactOpposites() {
        val cases =
            listOf(
                event("org.micoli.micraft.game.npc.NpcSpawner", "$SIMULATION_THREAD_PREFIX-2"),
                event("org.micoli.micraft.game.npc.NpcSpawner", "DefaultDispatcher-worker-3"),
                event("org.micoli.micraft.simulation.WorldSimulator", "main"),
                event("io.ktor.server.Application", "main"),
            )
        cases.forEach { event ->
            val server = serverAppender.decide(event)
            val simulation = simulationAppender.decide(event)
            assertTrue(
                (server == FilterReply.DENY) != (simulation == FilterReply.DENY),
                "an event must land in exactly one log: ${event.loggerName} on ${event.threadName}")
        }
    }
}
