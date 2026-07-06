package org.micoli.micraft

import org.micoli.micraft.babylon.*
import org.micoli.micraft.npc.NpcState

class NpcManager(private val scene: JsAny) {
    @OptIn(ExperimentalWasmJsInterop::class) private val npcModels = mutableMapOf<String, JsAny>()
    private val npcPrevPos = mutableMapOf<String, Triple<Double, Double, Double>>()
    private val pendingNpcs = mutableListOf<NpcState>()
    private val npcNames = mutableMapOf<String, String>()
    private var highlightedNpcId: String? = null

    fun handleSpawned(npc: NpcState) {
        npcNames[npc.id] = npc.name
        updateAutocomplete()
        jsSetNpcOnMinimap(npc.id, npc.pos.x, npc.pos.z)
        if (!jsIsNpcModelsReady()) {
            pendingNpcs.add(npc)
            return
        }
        createOrUpdateMesh(npc)
    }

    fun handleUpdate(npc: NpcState) {
        jsSetNpcOnMinimap(npc.id, npc.pos.x, npc.pos.z)
        if (!jsIsNpcModelsReady()) {
            pendingNpcs.removeAll { it.id == npc.id }
            pendingNpcs.add(npc)
            return
        }
        createOrUpdateMesh(npc)
    }

    fun handleDespawned(id: String) {
        npcNames.remove(id)
        updateAutocomplete()
        jsRemoveNpcFromMinimap(id)
        pendingNpcs.removeAll { it.id == id }
        npcModels.remove(id)?.let(::jsDisposeNpcModel)
        npcPrevPos.remove(id)
    }

    fun tick() {
        if (!jsIsNpcModelsReady() || pendingNpcs.isEmpty()) return
        val snapshot = pendingNpcs.toList()
        pendingNpcs.clear()
        snapshot.forEach { createOrUpdateMesh(it) }
    }

    fun clear() {
        npcModels.values.forEach(::jsDisposeNpcModel)
        npcModels.clear()
        npcPrevPos.clear()
        pendingNpcs.clear()
        npcNames.clear()
        jsSetNpcNames("[]")
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun createOrUpdateMesh(npc: NpcState) {
        val model = npcModels.getOrPut(npc.id) { jsCreateNpcModel(scene, npc.type) ?: return }
        val prev = npcPrevPos[npc.id]
        val x = npc.pos.x.toDouble()
        val y = npc.pos.y.toDouble()
        val z = npc.pos.z.toDouble()
        val isWalking =
            prev != null &&
                (kotlin.math.abs(x - prev.first) > 0.001 || kotlin.math.abs(z - prev.third) > 0.001)
        npcPrevPos[npc.id] = Triple(x, y, z)
        jsSetNpcTransform(model, x, y, z, npc.yaw, isWalking)
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    fun setHighlightTarget(id: String?) {
        val prevModel = highlightedNpcId?.let { npcModels[it] }
        if (prevModel != null) jsHighlightNpcModel(scene, prevModel, false)
        highlightedNpcId = id
        val nextModel = id?.let { npcModels[it] }
        if (nextModel != null) jsHighlightNpcModel(scene, nextModel, true)
    }

    fun cycleNearestNpc(
        playerX: Double,
        playerY: Double,
        playerZ: Double,
        playerYaw: Double,
        currentId: String?
    ): String? {
        val maxDist2 = 20.0 * 20.0
        val halfConeCos = kotlin.math.cos(kotlin.math.PI / 6.0) // cos(30°) — half of 60° cone
        val fwdX = kotlin.math.sin(playerYaw)
        val fwdZ = kotlin.math.cos(playerYaw)
        val sorted =
            npcPrevPos.entries
                .filter { (_, pos) ->
                    val dx = pos.first - playerX
                    val dy = pos.second - playerY
                    val dz = pos.third - playerZ
                    val dist2 = dx * dx + dy * dy + dz * dz
                    if (dist2 > maxDist2) return@filter false
                    val dist2D = kotlin.math.sqrt(dx * dx + dz * dz)
                    if (dist2D < 0.001) return@filter true
                    (dx * fwdX + dz * fwdZ) / dist2D >= halfConeCos
                }
                .sortedBy { (_, pos) ->
                    val dx = pos.first - playerX
                    val dy = pos.second - playerY
                    val dz = pos.third - playerZ
                    dx * dx + dy * dy + dz * dz
                }
                .map { it.key }
        if (sorted.isEmpty()) return null
        if (currentId == null) return sorted.first()
        val idx = sorted.indexOf(currentId)
        return if (idx < 0 || idx == sorted.lastIndex) null else sorted[idx + 1]
    }

    private fun updateAutocomplete() {
        val json = "[" + npcNames.values.joinToString(",") { "\"$it\"" } + "]"
        jsSetNpcNames(json)
    }
}
