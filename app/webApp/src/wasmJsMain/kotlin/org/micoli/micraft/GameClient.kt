package org.micoli.micraft

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.*
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.WorldConstants

class GameClient(private val scene: JsAny, private val camera: JsAny) {
    private val blockMeshes  = mutableMapOf<String, JsAny>()
    private val playerMeshes = mutableMapOf<String, JsAny>()
    private val scope        = CoroutineScope(Dispatchers.Default)
    private var localPlayerId: String? = null

    private val grassMat   = jsCreateMaterial("grass",   scene).also { jsSetMaterialColor(it, 0.3,  0.7,  0.2)  }
    private val stoneMat   = jsCreateMaterial("stone",   scene).also { jsSetMaterialColor(it, 0.5,  0.5,  0.5)  }
    private val dirtMat    = jsCreateMaterial("dirt",    scene).also { jsSetMaterialColor(it, 0.55, 0.35, 0.15) }
    private val bedrockMat = jsCreateMaterial("bedrock", scene).also { jsSetMaterialColor(it, 0.1,  0.1,  0.1)  }
    private val playerMat  = jsCreateMaterial("player",  scene).also { jsSetMaterialColor(it, 0.8,  0.2,  0.2)  }

    fun connect(host: String, port: Int) {
        scope.launch {
            try {
                val client = HttpClient(Js) { install(WebSockets) }
                client.webSocket(host = host, port = port, path = "/game") {
                    send(Frame.Text(Json.encodeToString<ClientMessage>(ClientMessage.Connect("Player"))))

                    // Send movement intents at server tick rate
                    val inputJob = launch {
                        while (isActive) {
                            delay(50)
                            val intent = buildMoveIntent()
                            send(Frame.Text(Json.encodeToString<ClientMessage>(intent)))
                        }
                    }

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val msg = runCatching {
                                Json.decodeFromString<ServerMessage>(frame.readText())
                            }.onFailure { e ->
                                jsError("JSON parse error: ${e.message}")
                            }.getOrNull() ?: continue
                            handleMessage(msg)
                        }
                    }
                    inputJob.cancel()
                }
            } catch (e: Throwable) {
                jsError("WebSocket error: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    private fun buildMoveIntent(): ClientMessage.MoveIntent {
        val fwdX  = jsGetCameraForwardX(camera).toFloat()
        val fwdZ  = jsGetCameraForwardZ(camera).toFloat()
        val rightX = fwdZ    // perpendicular in XZ plane
        val rightZ = -fwdX

        var dx = 0f; var dz = 0f
        if (jsIsKeyDown("KeyW")     || jsIsKeyDown("ArrowUp"))    { dx += fwdX;   dz += fwdZ   }
        if (jsIsKeyDown("KeyS")     || jsIsKeyDown("ArrowDown"))  { dx -= fwdX;   dz -= fwdZ   }
        if (jsIsKeyDown("KeyD")     || jsIsKeyDown("ArrowRight")) { dx += rightX; dz += rightZ }
        if (jsIsKeyDown("KeyA")     || jsIsKeyDown("ArrowLeft"))  { dx -= rightX; dz -= rightZ }

        // Normalize diagonal to avoid speed boost
        val len = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        if (len > 1f) { dx /= len; dz /= len }

        val stance = when {
            jsIsKeyDown("ControlLeft") -> PlayerStance.CRAWLING
            jsIsKeyDown("ShiftLeft")   -> PlayerStance.SNEAKING
            else                       -> PlayerStance.STANDING
        }

        return ClientMessage.MoveIntent(
            dx     = dx,
            dz     = dz,
            yaw    = jsGetCameraRotationY(camera).toFloat(),
            pitch  = jsGetCameraRotationX(camera).toFloat(),
            stance = stance,
            jump   = jsIsKeyDown("Space"),
        )
    }

    private fun handleMessage(msg: ServerMessage) {
        when (msg) {
            is ServerMessage.Welcome -> {
                localPlayerId = msg.playerId
            }
            is ServerMessage.ChunkData -> {
                renderChunk(msg.chunk)
            }
            is ServerMessage.PlayerUpdate -> {
                val s = msg.state
                if (s.id == localPlayerId) {
                    jsCameraSetPosition(camera,
                        s.pos.x.toDouble(),
                        (s.pos.y + s.stance.eyeOffset).toDouble(),
                        s.pos.z.toDouble())
                    val toDeg = 180.0 / kotlin.math.PI
                    jsUpdateHUD(
                        s.pos.x.toDouble(), s.pos.y.toDouble(), s.pos.z.toDouble(),
                        jsGetCameraRotationY(camera) * toDeg,
                        jsGetCameraRotationX(camera) * toDeg,
                        s.stance.name,
                    )
                } else {
                    updatePlayerMesh(s.id, s.pos.x.toDouble(), s.pos.y.toDouble(), s.pos.z.toDouble())
                }
            }
            is ServerMessage.PlayerLeft  -> removePlayer(msg.playerId)
            is ServerMessage.WorldUpdate -> msg.changes.forEach { change ->
                val key = "${change.pos.x},${change.pos.y},${change.pos.z}"
                blockMeshes.remove(key)?.let(::jsDisposeMesh)
                if (change.type != BlockType.AIR) addBlock(change.pos.x, change.pos.y, change.pos.z, change.type)
            }
        }
    }

    private fun renderChunk(chunk: Chunk) {
        val ox = chunk.pos.cx * WorldConstants.CHUNK_SIZE
        val oz = chunk.pos.cz * WorldConstants.CHUNK_SIZE
        for (x in 0 until WorldConstants.CHUNK_SIZE) {
            for (z in 0 until WorldConstants.CHUNK_SIZE) {
                for (y in 0..WorldConstants.WORLD_MAX_Y) {
                    val block = chunk.getBlock(x, y, z)
                    if (block == BlockType.AIR) continue
                    if (isHidden(chunk, x, y, z)) continue
                    addBlock(ox + x, y, oz + z, block)
                }
            }
        }
    }

    private fun isHidden(chunk: Chunk, x: Int, y: Int, z: Int): Boolean {
        val maxY = WorldConstants.WORLD_MAX_Y
        val s    = WorldConstants.CHUNK_SIZE
        if (y < maxY && chunk.getBlock(x, y + 1, z) == BlockType.AIR) return false
        if (y > 0    && chunk.getBlock(x, y - 1, z) == BlockType.AIR) return false
        if (x > 0    && chunk.getBlock(x - 1, y, z) == BlockType.AIR) return false
        if (x < s - 1 && chunk.getBlock(x + 1, y, z) == BlockType.AIR) return false
        if (z > 0    && chunk.getBlock(x, y, z - 1) == BlockType.AIR) return false
        if (z < s - 1 && chunk.getBlock(x, y, z + 1) == BlockType.AIR) return false
        return true
    }

    private fun addBlock(wx: Int, wy: Int, wz: Int, type: BlockType) {
        val key = "$wx,$wy,$wz"
        if (blockMeshes.containsKey(key)) return
        val mat = when (type) {
            BlockType.GRASS   -> grassMat
            BlockType.STONE   -> stoneMat
            BlockType.DIRT    -> dirtMat
            BlockType.BEDROCK -> bedrockMat
            BlockType.AIR     -> return
        }
        val mesh = jsCreateBox("b_$key", 1.0, scene)
        jsSetMeshPosition(mesh, wx.toDouble(), wy.toDouble(), wz.toDouble())
        jsSetMeshMaterial(mesh, mat)
        blockMeshes[key] = mesh
    }

    private fun updatePlayerMesh(id: String, x: Double, y: Double, z: Double) {
        val mesh = playerMeshes.getOrPut(id) {
            jsCreateBox("p_$id", 0.6, scene).also { jsSetMeshMaterial(it, playerMat) }
        }
        jsSetMeshPosition(mesh, x, y, z)
    }

    private fun removePlayer(id: String) {
        playerMeshes.remove(id)?.let(::jsDisposeMesh)
    }
}
