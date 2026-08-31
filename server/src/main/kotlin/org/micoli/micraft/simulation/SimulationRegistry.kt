package org.micoli.micraft.simulation

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.micoli.micraft.game.SharedGameServices
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SimulationRegistry::class.java)

/**
 * Live simulations, keyed by their own id rather than by the socket that created them: several
 * admins can watch the same arena, and an arena outlives the tab that started it.
 *
 * Two safeguards keep that from burning CPU forever: at most [MAX_SIMULATIONS] run at once, and an
 * arena nobody has watched for [IDLE_TIMEOUT_MS] is reaped by [reapIdle].
 */
class SimulationRegistry(private val depsProvider: () -> SimulationDeps) {

    /** Loaded once and shared by every arena — the same config the live server runs on. */
    private val shared: SharedGameServices by lazy { SharedGameServices.default() }

    private class Entry(
        val id: String,
        val simulator: WorldSimulator,
        val name: String,
        val startedAtMs: Long,
    ) {
        val viewers = AtomicInteger(0)
        @Volatile var lastViewerAtMs: Long = startedAtMs
    }

    private val simulations = ConcurrentHashMap<String, Entry>()

    val count: Int
        get() = simulations.size

    operator fun get(id: String?): WorldSimulator? = id?.let { simulations[it]?.simulator }

    /**
     * Start a new arena and return its id. Fails with [IllegalStateException] when the cap is
     * reached — refusing is better than quietly starving the arenas already running.
     */
    suspend fun start(config: SimulationConfig, name: String = ""): String {
        reapIdle()
        check(simulations.size < MAX_SIMULATIONS) {
            "trop de simulations en cours (${simulations.size}/$MAX_SIMULATIONS) — ferme-en une"
        }
        val id = UUID.randomUUID().toString()
        val simulator = WorldSimulator(config, depsProvider(), shared)
        val label = name.ifBlank { defaultName(config) }
        simulations[id] = Entry(id, simulator, label, System.currentTimeMillis())
        simulator.start()
        log.info("Simulation '{}' started as {} ({} live)", label, id.take(8), simulations.size)
        return id
    }

    /** Rebuild an arena from its own config, keeping its id so watchers stay attached. */
    suspend fun restart(id: String): WorldSimulator? {
        val previous = simulations[id] ?: return null
        previous.simulator.stop()
        val simulator = WorldSimulator(previous.simulator.config, depsProvider(), shared)
        val replacement = Entry(id, simulator, previous.name, System.currentTimeMillis())
        replacement.viewers.set(previous.viewers.get())
        simulations[id] = replacement
        simulator.start()
        log.info("Simulation {} restarted", id.take(8))
        return simulator
    }

    fun stop(id: String) {
        simulations.remove(id)?.let {
            it.simulator.stop()
            log.info("Simulation '{}' ({}) stopped", it.name, id.take(8))
        }
    }

    fun stopAll() {
        simulations.keys.toList().forEach { stop(it) }
    }

    // ── Viewers ───────────────────────────────────────────────────────────────

    fun addViewer(id: String): Boolean {
        val entry = simulations[id] ?: return false
        entry.viewers.incrementAndGet()
        entry.lastViewerAtMs = System.currentTimeMillis()
        return true
    }

    fun removeViewer(id: String) {
        val entry = simulations[id] ?: return
        if (entry.viewers.decrementAndGet() <= 0) {
            entry.viewers.set(0)
            entry.lastViewerAtMs = System.currentTimeMillis()
        }
    }

    /** Drop arenas nobody has been watching for a while. */
    fun reapIdle(now: Long = System.currentTimeMillis()) {
        simulations.values
            .filter { it.viewers.get() <= 0 && now - it.lastViewerAtMs > IDLE_TIMEOUT_MS }
            .forEach {
                log.info("Simulation '{}' ({}) reaped: no viewer", it.name, it.id.take(8))
                stop(it.id)
            }
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    fun list(): List<SimulationInfo> =
        simulations.values
            .sortedBy { it.startedAtMs }
            .map { entry ->
                val stats = entry.simulator.statsDto()
                SimulationInfo(
                    id = entry.id,
                    name = entry.name,
                    halfSize = entry.simulator.config.halfSize,
                    viewers = entry.viewers.get(),
                    startedAtMs = entry.startedAtMs,
                    tick = stats.tick,
                    gameDay = stats.gameDay,
                    npcCount = stats.npcCount,
                    populationCap = stats.populationCap,
                    configuredTps = stats.configuredTps,
                    realTps = stats.realTps,
                    paused = stats.paused,
                )
            }

    private fun defaultName(config: SimulationConfig): String {
        val size = config.halfSize * 2
        val types = config.initialSpawns.joinToString(", ") { "${it.count}×${it.type}" }
        return if (types.isBlank()) "arène ${size}×$size" else "arène ${size}×$size — $types"
    }

    companion object {
        const val MAX_SIMULATIONS = 4
        const val IDLE_TIMEOUT_MS = 5 * 60_000L
    }
}
