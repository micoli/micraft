package org.micoli.micraft

import kotlin.math.cos
import kotlin.math.sin
import org.micoli.micraft.babylon.*
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState

private const val PRED_DT = 16.0 / 1000.0

class RemotePlayerManager(private val scene: JsAny) {
    @OptIn(ExperimentalWasmJsInterop::class)
    private val playerModels = mutableMapOf<String, JsAny>()
    private val playerPrevPos = mutableMapOf<String, Triple<Double, Double, Double>>()
    private val playerCurrentPos = mutableMapOf<String, Triple<Double, Double, Double>>()
    private val playerTargetPos = mutableMapOf<String, Triple<Double, Double, Double>>()
    private val playerCurrentYaw = mutableMapOf<String, Float>()
    private val playerTargetYaw = mutableMapOf<String, Float>()
    private val playerTargetPitch = mutableMapOf<String, Float>()
    private val playerLerpT = mutableMapOf<String, Double>()
    private val playerNames = mutableMapOf<String, String>()
    private val playerSkins = mutableMapOf<String, String>()
    private val playerArmors = mutableMapOf<String, List<String>>()
    private val playerArmorsAttached = mutableMapOf<String, List<String>>()
    private val playerLightBoost = mutableMapOf<String, Boolean>()
    private val playerLightBoostApplied = mutableMapOf<String, Boolean>()
    private val playerStances = mutableMapOf<String, PlayerStance>()
    private val playerFlying = mutableMapOf<String, Boolean>()

    fun updateFromServer(state: PlayerState) {
        playerNames[state.id] = state.name
        updateAutocomplete()
        if (playerSkins[state.id] != state.skin) {
            playerSkins[state.id] = state.skin
            jsInitPlayerModel(state.skin)
            playerModels.remove(state.id)?.let(::jsDisposePlayerModel)
            playerArmorsAttached[state.id] = emptyList()
        }
        if (playerArmors[state.id] != state.armors) {
            playerArmors[state.id] = state.armors
            state.armors.forEach { jsInitArmorModel(it) }
        }
        val nx = state.pos.x.toDouble()
        val ny = state.pos.y.toDouble()
        val nz = state.pos.z.toDouble()
        playerCurrentPos[state.id] = playerTargetPos[state.id] ?: Triple(nx, ny, nz)
        playerCurrentYaw[state.id] = playerTargetYaw[state.id] ?: state.orientation.yaw
        playerTargetPos[state.id] = Triple(nx, ny, nz)
        playerTargetYaw[state.id] = state.orientation.yaw
        playerTargetPitch[state.id] = state.orientation.pitch
        playerLerpT[state.id] = 0.0
        playerLightBoost[state.id] = state.lightBoostEnabled
        playerStances[state.id] = state.stance
        playerFlying[state.id] = state.flying
    }

    fun remove(id: String) {
        playerNames.remove(id)
        playerSkins.remove(id)
        playerArmors.remove(id)
        playerArmorsAttached.remove(id)
        updateAutocomplete()
        playerModels.remove(id)?.let(::jsDisposePlayerModel)
        jsRemovePlayerFromMinimap(id)
        playerPrevPos.remove(id)
        playerCurrentPos.remove(id)
        playerTargetPos.remove(id)
        playerCurrentYaw.remove(id)
        playerTargetYaw.remove(id)
        playerTargetPitch.remove(id)
        playerLerpT.remove(id)
        playerLightBoost.remove(id)
        playerLightBoostApplied.remove(id)
        playerStances.remove(id)
        playerFlying.remove(id)
    }

    fun tick() {
        for (id in playerTargetPos.keys.toList()) {
            val skin = playerSkins[id] ?: "articulated"
            if (!jsIsPlayerBbmodelReady(skin)) continue
            val cur = playerCurrentPos[id] ?: continue
            val tgt = playerTargetPos[id] ?: continue
            val t = ((playerLerpT[id] ?: 0.0) + PRED_DT / 0.050).coerceAtMost(1.0)
            playerLerpT[id] = t
            val x = cur.first + (tgt.first - cur.first) * t
            val y = cur.second + (tgt.second - cur.second) * t
            val z = cur.third + (tgt.third - cur.third) * t
            val curYaw = playerCurrentYaw[id] ?: 0f
            val tgtYaw = playerTargetYaw[id] ?: 0f
            val yaw = curYaw + (tgtYaw - curYaw) * t.toFloat()
            val pitch = playerTargetPitch[id] ?: 0f
            val model = playerModels.getOrPut(id) { jsCreatePlayerModelNow(scene, skin) }
            val prev = playerPrevPos[id]
            val moveX = if (prev != null) x - prev.first else 0.0
            val moveZ = if (prev != null) z - prev.third else 0.0
            val moving = kotlin.math.abs(moveX) > 0.001 || kotlin.math.abs(moveZ) > 0.001
            playerPrevPos[id] = Triple(x, y, z)

            val stance = playerStances[id] ?: PlayerStance.STANDING
            val flying = playerFlying[id] ?: false
            val animClip =
                when {
                    flying -> "jump_idle"
                    stance == PlayerStance.CRAWLING -> "crawling"
                    stance == PlayerStance.SNEAKING -> "sneaking"
                    !moving -> "idle"
                    else -> {
                        // Forward/right basis matching the same yaw convention as the local
                        // player's
                        // camera-forward vector (LocalPlayerController.kt): fwd = (sin(yaw),
                        // cos(yaw)).
                        val fwdX = sin(yaw.toDouble())
                        val fwdZ = cos(yaw.toDouble())
                        val rightX = fwdZ
                        val rightZ = -fwdX
                        val forwardDot = moveX * fwdX + moveZ * fwdZ
                        val lateralDot = moveX * rightX + moveZ * rightZ
                        if (kotlin.math.abs(forwardDot) >= kotlin.math.abs(lateralDot)) {
                            if (forwardDot >= 0) "walking_forward" else "walking_backward"
                        } else {
                            if (lateralDot >= 0) "strafe_right" else "strafe_left"
                        }
                    }
                }
            jsSetPlayerTransform(model, x, y, z, yaw, pitch, animClip)
            jsSetPlayerOnMinimap(id, x.toFloat(), z.toFloat(), yaw)

            val wanted = playerArmors[id] ?: emptyList()
            val attached = playerArmorsAttached[id] ?: emptyList()
            if (wanted != attached) {
                (attached - wanted.toSet()).forEach { jsDetachArmor(model, it) }
                val toAttach = wanted - attached.toSet()
                val readyToAttach = toAttach.filter { jsIsArmorModelReady(it) }
                readyToAttach.forEach { jsAttachArmor(model, it, scene) }
                playerArmorsAttached[id] = attached - toAttach.toSet() + readyToAttach
            }

            val lightBoost = playerLightBoost[id] ?: false
            if (lightBoost != (playerLightBoostApplied[id] ?: false)) {
                jsSetRemotePlayerLight(model, scene, lightBoost)
                playerLightBoostApplied[id] = lightBoost
            }
        }
    }

    fun clear() {
        playerModels.keys.forEach(::jsRemovePlayerFromMinimap)
        playerModels.values.forEach(::jsDisposePlayerModel)
        playerModels.clear()
        playerPrevPos.clear()
        playerCurrentPos.clear()
        playerTargetPos.clear()
        playerCurrentYaw.clear()
        playerTargetYaw.clear()
        playerTargetPitch.clear()
        playerLerpT.clear()
        playerNames.clear()
        playerSkins.clear()
        playerArmors.clear()
        playerArmorsAttached.clear()
        playerLightBoost.clear()
        playerLightBoostApplied.clear()
        playerStances.clear()
        playerFlying.clear()
        jsSetConnectedPlayers("[]")
    }

    fun nearestLightBoostPosition(
        observerX: Double,
        observerZ: Double
    ): Triple<Double, Double, Double>? =
        playerTargetPos.entries
            .filter { (id, _) -> playerLightBoost[id] == true }
            .minByOrNull { (_, pos) ->
                val dx = pos.first - observerX
                val dz = pos.third - observerZ
                dx * dx + dz * dz
            }
            ?.value

    private fun updateAutocomplete() {
        val json = "[" + playerNames.values.joinToString(",") { "\"$it\"" } + "]"
        jsSetConnectedPlayers(json)
    }
}
