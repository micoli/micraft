package org.micoli.micraft.simulation

import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SimulationRegistry::class.java)

/**
 * Live simulations, one per admin websocket. Closing the socket stops the simulation, so no arena
 * keeps ticking without a viewer.
 */
class SimulationRegistry(private val depsProvider: () -> SimulationDeps) {
    private val simulations = ConcurrentHashMap<String, WorldSimulator>()

    val count: Int
        get() = simulations.size

    operator fun get(key: String): WorldSimulator? = simulations[key]

    /** Replace any existing simulation for [key] with a fresh one built from [config]. */
    suspend fun start(key: String, config: SimulationConfig): WorldSimulator {
        stop(key)
        val simulator = WorldSimulator(config, depsProvider())
        simulations[key] = simulator
        simulator.start()
        log.info("Simulation started for {} ({} live)", key.take(8), simulations.size)
        return simulator
    }

    /** Rebuild with the exact same config — the quick-restart button. */
    suspend fun restart(key: String): WorldSimulator? {
        val config = simulations[key]?.config ?: return null
        return start(key, config)
    }

    fun stop(key: String) {
        simulations.remove(key)?.let {
            it.stop()
            log.info("Simulation stopped for {}", key.take(8))
        }
    }

    fun stopAll() {
        simulations.keys.toList().forEach { stop(it) }
    }
}
