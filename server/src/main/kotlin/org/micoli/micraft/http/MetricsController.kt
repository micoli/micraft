package org.micoli.micraft.http

import com.sun.management.OperatingSystemMXBean
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.lang.management.ManagementFactory
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.TICKS_PER_DAY
import org.micoli.micraft.game.TICK_MS
import org.micoli.micraft.game.tick.TickPhaseStat

// Rough per-entry sizes for working-set estimates (no JAMM/JOL available):
// BlockPos = 3 Int + obj header ≈ 28 B; ConcurrentHashMap node ≈ 56 B.
private const val LIQUID_BYTES_PER_ENTRY = 284L // 3 maps × (28 + 56) + keys shared
private const val VEGETATION_BYTES_PER_ENTRY = 182L // GrowingBlock ≈ 126 B + key 56 B
private const val NPC_BYTES_PER_ENTRY = 512L // NpcInstance + state + behavior ref

private fun gauge(
    name: String,
    help: String,
    value: Number,
    labels: Map<String, String> = emptyMap()
): String {
    val labelStr =
        if (labels.isEmpty()) ""
        else labels.entries.joinToString(",", "{", "}") { (k, v) -> """$k="$v"""" }
    return "# HELP $name $help\n# TYPE $name gauge\n$name$labelStr $value\n"
}

private fun counter(name: String, help: String, value: Number): String =
    "# HELP $name $help\n# TYPE $name counter\n$name $value\n"

fun buildPrometheusMetrics(gameLoop: GameLoop): String {
    val memMx = ManagementFactory.getMemoryMXBean()
    val heapUsed = memMx.heapMemoryUsage.used
    val heapMax = memMx.heapMemoryUsage.max
    val nonHeapUsed = memMx.nonHeapMemoryUsage.used
    val osMx = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    val threadMx = ManagementFactory.getThreadMXBean()

    val npcStates = gameLoop.getNpcStates()
    val npcByType = npcStates.groupingBy { it.type }.eachCount()
    val activeLiquids = gameLoop.getActiveLiquidCount()
    val pendingLiquidTicks = gameLoop.getLiquidPendingTickCount()
    val activeVegetation = gameLoop.getActiveVegetationCount()
    val net = gameLoop.networkStats

    return buildString {
        // Players
        append(
            gauge(
                "micraft_connected_players",
                "Number of connected players",
                gameLoop.getPlayerStates().size))

        // NPCs
        append(gauge("micraft_npc_total", "Total NPCs alive", npcStates.size))
        if (npcByType.isNotEmpty()) {
            append(
                "# HELP micraft_npc_by_type NPCs alive by type\n# TYPE micraft_npc_by_type gauge\n")
            npcByType.forEach { (type, count) ->
                append("""micraft_npc_by_type{type="$type"} $count""").append('\n')
            }
        }
        append(
            gauge(
                "micraft_npc_manager_estimated_bytes",
                "NpcManager estimated heap usage (bytes)",
                npcStates.size * NPC_BYTES_PER_ENTRY))

        // World items
        append(
            gauge(
                "micraft_world_items_total",
                "World items (drops) on the ground",
                gameLoop.getWorldItemCount()))

        // World state
        append(
            gauge(
                "micraft_loaded_chunks_total",
                "Loaded/generated chunks in memory",
                gameLoop.getLoadedChunkCount()))
        append(counter("micraft_game_ticks_total", "Game tick counter", gameLoop.getGameTicks()))

        // Network traffic
        append(
            counter(
                "micraft_network_bytes_in_total",
                "Total WebSocket bytes received from clients",
                net.bytesIn.get()))
        append(
            counter(
                "micraft_network_bytes_out_total",
                "Total WebSocket bytes sent to clients",
                net.bytesOut.get()))

        // LiquidManager
        append(
            gauge(
                "micraft_liquid_active_blocks",
                "LiquidManager: active liquid blocks",
                activeLiquids))
        append(
            gauge(
                "micraft_liquid_pending_ticks",
                "LiquidManager: blocks with pending tick countdown",
                pendingLiquidTicks))
        append(
            gauge(
                "micraft_liquid_manager_estimated_bytes",
                "LiquidManager estimated heap usage (bytes)",
                activeLiquids * LIQUID_BYTES_PER_ENTRY))

        // VegetationManager
        append(
            gauge(
                "micraft_vegetation_active_blocks",
                "VegetationManager: blocks in growth queue",
                activeVegetation))
        append(
            gauge(
                "micraft_vegetation_manager_estimated_bytes",
                "VegetationManager estimated heap usage (bytes)",
                activeVegetation * VEGETATION_BYTES_PER_ENTRY))

        // JVM
        append(gauge("jvm_heap_used_bytes", "JVM heap memory used in bytes", heapUsed))
        append(gauge("jvm_heap_max_bytes", "JVM heap memory max in bytes", heapMax))
        append(gauge("jvm_nonheap_used_bytes", "JVM non-heap memory used in bytes", nonHeapUsed))
        append(
            gauge(
                "jvm_processors",
                "Available processor count",
                Runtime.getRuntime().availableProcessors()))
        append(
            gauge(
                "jvm_process_cpu_load_ratio",
                "Process CPU load, 0.0-1.0",
                osMx.processCpuLoad.coerceAtLeast(0.0)))
        append(
            gauge(
                "jvm_system_cpu_load_ratio",
                "System CPU load, 0.0-1.0",
                osMx.cpuLoad.coerceAtLeast(0.0)))
        append(gauge("jvm_thread_count", "Live JVM thread count", threadMx.threadCount))
        append(gauge("jvm_thread_peak_count", "Peak JVM thread count", threadMx.peakThreadCount))
        append(
            "# HELP jvm_gc_collection_count_total GC collection count by collector\n# TYPE jvm_gc_collection_count_total counter\n")
        append(
            "# HELP jvm_gc_collection_time_ms_total GC collection time (ms) by collector\n# TYPE jvm_gc_collection_time_ms_total counter\n")
        ManagementFactory.getGarbageCollectorMXBeans().forEach { gc ->
            append("""jvm_gc_collection_count_total{gc="${gc.name}"} ${gc.collectionCount}""")
                .append('\n')
            append("""jvm_gc_collection_time_ms_total{gc="${gc.name}"} ${gc.collectionTime}""")
                .append('\n')
        }

        // Per-subsystem tick CPU breakdown (EMA, ms/tick)
        val tickProfile = gameLoop.getTickProfile()
        if (tickProfile.isNotEmpty()) {
            append(
                "# HELP micraft_tick_phase_avg_ms Per-subsystem tick duration, EMA in ms\n# TYPE micraft_tick_phase_avg_ms gauge\n")
            tickProfile.forEach { p ->
                append("""micraft_tick_phase_avg_ms{phase="${p.name}"} ${p.avgMs}""").append('\n')
            }
        }
        append(gauge("micraft_tick_budget_ms", "Configured tick period (ms)", TICK_MS))
    }
}

fun buildStatusSnapshot(gameLoop: GameLoop): StatusSnapshot {
    val memMx = ManagementFactory.getMemoryMXBean()
    val heapUsed = memMx.heapMemoryUsage.used
    val heapMax = memMx.heapMemoryUsage.max
    val nonHeapUsed = memMx.nonHeapMemoryUsage.used

    val osMx = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    val threadMx = ManagementFactory.getThreadMXBean()
    val gcStats =
        ManagementFactory.getGarbageCollectorMXBeans().map {
            GcStat(
                name = it.name,
                collectionCount = it.collectionCount,
                collectionTimeMs = it.collectionTime)
        }
    val runtimeMx = ManagementFactory.getRuntimeMXBean()

    val npcStates = gameLoop.getNpcStates()
    val activeLiquids = gameLoop.getActiveLiquidCount()
    val pendingLiquidTicks = gameLoop.getLiquidPendingTickCount()
    val activeVegetation = gameLoop.getActiveVegetationCount()
    val net = gameLoop.networkStats

    return StatusSnapshot(
        connectedPlayers = gameLoop.getPlayerStates().size,
        playerNames = gameLoop.getPlayerStates().map { it.name },
        npcTotal = npcStates.size,
        npcByType = npcStates.groupingBy { it.type }.eachCount(),
        npcEstBytes = npcStates.size * NPC_BYTES_PER_ENTRY,
        worldItems = gameLoop.getWorldItemCount(),
        loadedChunks = gameLoop.getLoadedChunkCount(),
        gameTicks = gameLoop.getGameTicks(),
        networkBytesIn = net.bytesIn.get(),
        networkBytesOut = net.bytesOut.get(),
        activeLiquids = activeLiquids,
        pendingLiquidTicks = pendingLiquidTicks,
        liquidEstBytes = activeLiquids * LIQUID_BYTES_PER_ENTRY,
        activeVegetation = activeVegetation,
        vegetationEstBytes = activeVegetation * VEGETATION_BYTES_PER_ENTRY,
        heapUsedMb = heapUsed / 1_048_576,
        heapMaxMb = heapMax / 1_048_576,
        nonHeapUsedMb = nonHeapUsed / 1_048_576,
        processors = Runtime.getRuntime().availableProcessors(),
        ticksPerDay = TICKS_PER_DAY,
        processCpuLoadPct = (osMx.processCpuLoad * 100).coerceAtLeast(0.0),
        systemCpuLoadPct = (osMx.cpuLoad * 100).coerceAtLeast(0.0),
        threadCount = threadMx.threadCount,
        peakThreadCount = threadMx.peakThreadCount,
        uptimeMs = runtimeMx.uptime,
        gcStats = gcStats,
        tickProfile = gameLoop.getTickProfile().filter { it.name != "total" },
        avgTickDurationMs = gameLoop.getTickProfile().find { it.name == "total" }?.avgMs ?: 0.0,
        tickBudgetMs = TICK_MS,
    )
}

@Serializable
data class StatusSnapshot(
    val connectedPlayers: Int,
    val playerNames: List<String>,
    val npcTotal: Int,
    val npcByType: Map<String, Int>,
    val npcEstBytes: Long,
    val worldItems: Int,
    val loadedChunks: Int,
    val gameTicks: Long,
    val networkBytesIn: Long,
    val networkBytesOut: Long,
    val activeLiquids: Int,
    val pendingLiquidTicks: Int,
    val liquidEstBytes: Long,
    val activeVegetation: Int,
    val vegetationEstBytes: Long,
    val heapUsedMb: Long,
    val heapMaxMb: Long,
    val nonHeapUsedMb: Long,
    val processors: Int,
    val ticksPerDay: Long,
    val processCpuLoadPct: Double,
    val systemCpuLoadPct: Double,
    val threadCount: Int,
    val peakThreadCount: Int,
    val uptimeMs: Long,
    val gcStats: List<GcStat>,
    val tickProfile: List<TickPhaseStat>,
    val avgTickDurationMs: Double,
    val tickBudgetMs: Long,
)

@Serializable
data class GcStat(val name: String, val collectionCount: Long, val collectionTimeMs: Long)

// Not OpenAPI-documented: Prometheus text format and an HTML dashboard, neither a JSON API.
// The Application.kt pathFilter (segments not under /api or /auth) already excludes these from
// the exported spec regardless of which routing DSL is used here.
class MetricsController(private val gameLoop: GameLoop) {
    fun register(route: Route) =
        route.apply {
            get("/metrics") {
                call.respondText(buildPrometheusMetrics(gameLoop), ContentType.Text.Plain)
            }

            get("/status") {
                val s = buildStatusSnapshot(gameLoop)
                call.respondText(buildStatusHtml(s), ContentType.Text.Html)
            }
        }
}

private fun kb(bytes: Long) = if (bytes < 1024) "${bytes} B" else "${bytes / 1024} KB"

private fun buildStatusHtml(s: StatusSnapshot): String {
    val heapPct = if (s.heapMaxMb > 0) s.heapUsedMb * 100 / s.heapMaxMb else 0
    val npcRows =
        if (s.npcByType.isEmpty()) "<tr><td colspan='2' style='color:#555'>none</td></tr>"
        else
            s.npcByType.entries
                .sortedByDescending { it.value }
                .joinToString("") { (t, c) -> "<tr><td>${esc(t)}</td><td>$c</td></tr>" }
    val playerList =
        if (s.playerNames.isEmpty()) "<span style='color:#555'>none</span>"
        else s.playerNames.joinToString(", ") { esc(it) }

    return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta http-equiv="refresh" content="5">
<title>MicCraft Status</title>
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  body{background:#111;color:#ddd;font-family:monospace;padding:24px;font-size:14px}
  h1{color:#6af;font-size:20px;margin-bottom:20px}
  .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:16px}
  .card{background:#1a1a1a;border:1px solid #2a2a2a;border-radius:6px;padding:16px}
  .card h2{font-size:11px;color:#888;text-transform:uppercase;letter-spacing:1px;margin-bottom:12px}
  .big{font-size:32px;font-weight:bold;color:#6af}
  .label{font-size:11px;color:#555;margin-bottom:2px}
  .bar-wrap{background:#222;border-radius:3px;height:8px;margin:6px 0}
  .bar{background:#6af;height:8px;border-radius:3px}
  table{width:100%;border-collapse:collapse;font-size:12px}
  td{padding:3px 6px;border-bottom:1px solid #222}
  td:last-child{text-align:right;color:#6af}
  .row{display:flex;justify-content:space-between;margin:3px 0;font-size:12px}
  .val{color:#6af}
  .est{color:#555;font-size:10px}
  .footer{margin-top:20px;font-size:10px;color:#444}
  a{color:#446}
</style>
</head>
<body>
<h1>MicCraft Server Status</h1>
<div class="grid">

  <div class="card">
    <h2>Players</h2>
    <div class="big">${s.connectedPlayers}</div>
    <div style="margin-top:8px;font-size:12px;color:#aaa">$playerList</div>
  </div>

  <div class="card">
    <h2>NpcManager</h2>
    <div class="big">${s.npcTotal}</div>
    <table style="margin-top:8px">$npcRows</table>
    <div class="row" style="margin-top:8px"><span class="est">est. heap</span><span class="est">${kb(s.npcEstBytes)}</span></div>
  </div>

  <div class="card">
    <h2>LiquidManager</h2>
    <div class="row"><span>Active blocks</span><span class="val">${s.activeLiquids}</span></div>
    <div class="row"><span>Pending ticks</span><span class="val">${s.pendingLiquidTicks}</span></div>
    <div class="row" style="margin-top:6px"><span class="est">est. heap</span><span class="est">${kb(s.liquidEstBytes)}</span></div>
  </div>

  <div class="card">
    <h2>VegetationManager</h2>
    <div class="row"><span>Growing blocks</span><span class="val">${s.activeVegetation}</span></div>
    <div class="row" style="margin-top:6px"><span class="est">est. heap</span><span class="est">${kb(s.vegetationEstBytes)}</span></div>
  </div>

  <div class="card">
    <h2>World</h2>
    <div class="row"><span>Loaded chunks</span><span class="val">${s.loadedChunks}</span></div>
    <div class="row"><span>Ground items</span><span class="val">${s.worldItems}</span></div>
    <div class="row"><span>Game ticks</span><span class="val">${s.gameTicks}</span></div>
  </div>

  <div class="card">
    <h2>Network Traffic</h2>
    <div class="row"><span>&#x2193; Received</span><span class="val">${kb(s.networkBytesIn)}</span></div>
    <div class="row"><span>&#x2191; Sent</span><span class="val">${kb(s.networkBytesOut)}</span></div>
  </div>

  <div class="card">
    <h2>JVM Memory</h2>
    <div class="label">Heap ${s.heapUsedMb} MB / ${s.heapMaxMb} MB ($heapPct%)</div>
    <div class="bar-wrap"><div class="bar" style="width:${heapPct}%"></div></div>
    <div class="row"><span>Non-heap</span><span class="val">${s.nonHeapUsedMb} MB</span></div>
    <div class="row"><span>Processors</span><span class="val">${s.processors}</span></div>
  </div>

  <div class="card">
    <h2>CPU &amp; Threads</h2>
    <div class="row"><span>Process CPU</span><span class="val">${"%.1f".format(s.processCpuLoadPct)}%</span></div>
    <div class="row"><span>System CPU</span><span class="val">${"%.1f".format(s.systemCpuLoadPct)}%</span></div>
    <div class="row"><span>Threads</span><span class="val">${s.threadCount} (peak ${s.peakThreadCount})</span></div>
    <div class="row"><span>Uptime</span><span class="val">${s.uptimeMs / 60000} min</span></div>
    <table style="margin-top:8px">${s.gcStats.joinToString("") { g -> "<tr><td>${esc(g.name)}</td><td>${g.collectionCount} / ${g.collectionTimeMs}ms</td></tr>" }}</table>
  </div>

  <div class="card">
    <h2>Tick Breakdown (ms/tick, EMA)</h2>
    <div class="row"><span>Total tick duration</span><span class="val">${"%.2f".format(s.avgTickDurationMs)} ms / ${s.tickBudgetMs} ms budget</span></div>
    <table style="margin-top:8px">${s.tickProfile.joinToString("") { p -> "<tr><td>${esc(p.name)}</td><td>${"%.2f".format(p.avgMs)}</td></tr>" }}</table>
  </div>

</div>
<div class="footer">Auto-refreshes every 5 s &mdash; <a href="/metrics">Prometheus /metrics</a> &mdash; est. heap = rough entry-count × struct size, not measured</div>
</body>
</html>"""
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
