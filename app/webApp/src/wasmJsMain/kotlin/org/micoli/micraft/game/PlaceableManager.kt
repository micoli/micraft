package org.micoli.micraft.game

import org.micoli.micraft.babylon.*
import org.micoli.micraft.placeable.PlaceableState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage

// Trimmed fork of VehicleManager.kt: same spawn/update/despawn lifecycle, minus rail-derived
// yaw/pitch — a placeable's only orientation is its rotationStep (30° increments), rendered
// directly with no interpolation buffer since a placeable never moves once spawned.
class PlaceableManager(private val scene: JsAny) : ServerMessageHandler {
    @OptIn(ExperimentalWasmJsInterop::class)
    private val placeableModels = mutableMapOf<String, JsAny>()
    private val placeableTypes = mutableMapOf<String, String>()
    private val placeablePositions = mutableMapOf<String, Vec3>()
    private val placeableRotationSteps = mutableMapOf<String, Int>()
    private val pendingPlaceables = mutableListOf<PlaceableState>()

    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.PlaceableSpawned -> handleSpawned(msg.state)
            is ServerMessage.PlaceableUpdate -> handleUpdate(msg.state)
            is ServerMessage.PlaceableDespawned -> handleDespawned(msg.id)
            else -> Unit
        }

    fun handleSpawned(state: PlaceableState) {
        placeableTypes[state.id] = state.placeableType.id
        placeablePositions[state.id] = state.pos
        placeableRotationSteps[state.id] = state.rotationStep
        if (!jsIsPlaceableModelsReady()) {
            pendingPlaceables.add(state)
            return
        }
        ensureMesh(state)
    }

    fun handleUpdate(state: PlaceableState) {
        placeablePositions[state.id] = state.pos
        placeableRotationSteps[state.id] = state.rotationStep
        if (!jsIsPlaceableModelsReady()) {
            pendingPlaceables.removeAll { it.id == state.id }
            pendingPlaceables.add(state)
            return
        }
        ensureMesh(state)
        placeableModels[state.id]?.let {
            jsSetPlaceableTransform(
                it,
                state.pos.x.toDouble(),
                state.pos.y.toDouble(),
                state.pos.z.toDouble(),
                state.rotationStep)
        }
    }

    fun handleDespawned(id: String) {
        placeableTypes.remove(id)
        placeablePositions.remove(id)
        placeableRotationSteps.remove(id)
        pendingPlaceables.removeAll { it.id == id }
        placeableModels.remove(id)?.let(::jsDisposePlaceableModel)
    }

    fun tick() {
        if (jsIsPlaceableModelsReady() && pendingPlaceables.isNotEmpty()) {
            val batch = pendingPlaceables.toList()
            pendingPlaceables.clear()
            batch.forEach { ensureMesh(it) }
        }
    }

    @OptIn(ExperimentalWasmJsInterop::class) fun modelsMap(): Map<String, JsAny> = placeableModels

    fun positionsMap(): Map<String, Vec3> = placeablePositions.toMap()

    fun getType(id: String): String? = placeableTypes[id]

    fun getPosition(id: String): Vec3? = placeablePositions[id]

    fun getRotationStep(id: String): Int? = placeableRotationSteps[id]

    fun clear() {
        placeableModels.values.forEach(::jsDisposePlaceableModel)
        placeableModels.clear()
        placeableTypes.clear()
        placeablePositions.clear()
        placeableRotationSteps.clear()
        pendingPlaceables.clear()
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun ensureMesh(state: PlaceableState) {
        if (state.id in placeableModels) return
        val model = jsCreatePlaceableModel(scene, state.placeableType.id) ?: return
        placeableModels[state.id] = model
        jsSetPlaceableTransform(
            model,
            state.pos.x.toDouble(),
            state.pos.y.toDouble(),
            state.pos.z.toDouble(),
            state.rotationStep)
    }
}
