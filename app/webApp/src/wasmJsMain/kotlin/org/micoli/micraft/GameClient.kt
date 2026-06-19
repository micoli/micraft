package org.micoli.micraft

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.*
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.WorldConstants

class GameClient(private val scene: JsAny) {
    private val blockMeshes = mutableMapOf<String, JsAny>()
    private val playerMeshes = mutableMapOf<String, JsAny>()
    private val scope = CoroutineScope(Dispatchers.Default)

    private val grassMat   = jsCreateMaterial("grass",   scene).also { jsSetMaterialColor(it, 0.3,  0.7,  0.2)  }
    private val stoneMat   = jsCreateMaterial("stone",   scene).also { jsSetMaterialColor(it, 0.5,  0.5,  0.5)  }
    private val dirtMat    = jsCreateMaterial("dirt",    scene).also { jsSetMaterialColor(it, 0.55, 0.35, 0.15) }
    private val bedrockMat = jsCreateMaterial("bedrock", scene).also { jsSetMaterialColor(it, 0.1,  0.1,  0.1)  }
    private val playerMat  = jsCreateMaterial("player",  scene).also { jsSetMaterialColor(it, 0.8,  0.2,  0.2)  }

    fun connect(host: String, port: Int) {
        jsLog("connect() called → ws://$host:$port/ws")
        scope.launch {
            try {
                jsLog("HttpClient creating…")
                val client = HttpClient(Js) { install(WebSockets) }
                jsLog("HttpClient created, opening WebSocket…")
                client.webSocket(host = host, port = port, path = "/ws") {
                    jsLog("WebSocket opened, sending Connect message")
                    send(Frame.Text(Json.encodeToString<ClientMessage>(ClientMessage.Connect("Player"))))
                    jsLog("Connect sent, waiting for messages…")
                    var msgCount = 0
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            msgCount++
                            if (msgCount <= 5 || msgCount % 50 == 0) {
                                jsLog("frame #$msgCount (${text.length} chars): ${text.take(80)}")
                            }
                            val msg = runCatching {
                                Json.decodeFromString<ServerMessage>(text)
                            }.onFailure { e ->
                                jsError("JSON parse error: ${e.message} — raw: ${text.take(120)}")
                            }.getOrNull() ?: continue
                            handleMessage(msg)
                        }
                    }
                    jsLog("WebSocket incoming channel closed after $msgCount messages")
                }
            } catch (e: Throwable) {
                jsError("WebSocket error: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    private fun handleMessage(msg: ServerMessage) {
        when (msg) {
            is ServerMessage.Welcome     -> jsLog("Welcome: playerId=${msg.playerId} spawn=${msg.spawnPos}")
            is ServerMessage.ChunkData   -> {
                jsLog("ChunkData: chunk=${msg.chunk.pos}")
                renderChunk(msg.chunk)
                jsLog("ChunkData rendered: total blocks=${blockMeshes.size}")
            }
            is ServerMessage.PlayerUpdate -> {
                val s = msg.state
                updatePlayerMesh(s.id, s.pos.x.toDouble(), s.pos.y.toDouble(), s.pos.z.toDouble())
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
        var added = 0
        var skipped = 0
        for (x in 0 until WorldConstants.CHUNK_SIZE) {
            for (z in 0 until WorldConstants.CHUNK_SIZE) {
                for (y in 0..WorldConstants.WORLD_MAX_Y) {
                    val block = chunk.getBlock(x, y, z)
                    if (block == BlockType.AIR) continue
                    if (isHidden(chunk, x, y, z)) { skipped++; continue }
                    addBlock(ox + x, y, oz + z, block)
                    added++
                }
            }
        }
        jsLog("  chunk ${chunk.pos}: +$added meshes, $skipped hidden")
    }

    private fun isHidden(chunk: Chunk, x: Int, y: Int, z: Int): Boolean {
        val maxY = WorldConstants.WORLD_MAX_Y
        val s = WorldConstants.CHUNK_SIZE
        if (y < maxY && chunk.getBlock(x, y + 1, z) == BlockType.AIR) return false
        if (y > 0    && chunk.getBlock(x, y - 1, z) == BlockType.AIR) return false
        if (x > 0       && chunk.getBlock(x - 1, y, z) == BlockType.AIR) return false
        if (x < s - 1   && chunk.getBlock(x + 1, y, z) == BlockType.AIR) return false
        if (z > 0       && chunk.getBlock(x, y, z - 1) == BlockType.AIR) return false
        if (z < s - 1   && chunk.getBlock(x, y, z + 1) == BlockType.AIR) return false
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
            jsLog("new player mesh: $id")
            val m = jsCreateBox("p_$id", 0.6, scene)
            jsSetMeshMaterial(m, playerMat)
            m
        }
        jsSetMeshPosition(mesh, x, y, z)
    }

    private fun removePlayer(id: String) {
        jsLog("player left: $id")
        playerMeshes.remove(id)?.let(::jsDisposeMesh)
    }
}
