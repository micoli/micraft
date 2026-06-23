package org.micoli.micraft.npc

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldState
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val log = LoggerFactory.getLogger(NpcManager::class.java)

private fun Float.round1() = (Math.round(this * 1) / 1f)
private fun NpcState.round1() = copy(
    pos = pos.copy(x = pos.x.round1(), y = pos.y.round1(), z = pos.z.round1()),
    yaw = yaw.round1(),
)

class NpcManager(
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val getSessions: () -> Collection<PlayerSession> = { emptyList() },
) {
    private val npcs = ConcurrentHashMap<String, NpcInstance>()
    @Volatile private var definitions: Map<String, NpcDefinition> = emptyMap()
    private val lastSentToPlayer = ConcurrentHashMap<String, ConcurrentHashMap<String, NpcState>>()

    fun loadDefinitions(defs: Map<String, NpcDefinition>) {
        definitions = defs
    }

    fun reloadDefinitions(newDefs: Map<String, NpcDefinition>) {
        definitions = newDefs
        npcs.values.forEach { instance ->
            val newDef = newDefs[instance.definition.type]
            if (newDef != null) {
                instance.definition.let {  }
                // Replace definition by recreating is not safe without locks; mark for next spawn
                // Live instances keep their old definition until respawn
            }
        }
        log.info("NPC definitions reloaded: {} types", newDefs.size)
    }

    fun load(savePath: Path) {
        if (!savePath.exists()) {
            log.info("No NPC save file at {}", savePath)
            return
        }
        runCatching {
            val states = Json.decodeFromString(ListSerializer(NpcState.serializer()), savePath.readText())
            var loaded = 0
            for (state in states) {
                val def = definitions[state.type]
                if (def == null) {
                    log.warn("Unknown NPC type '{}' in save file — skipped", state.type)
                    continue
                }
                npcs[state.id] = NpcInstance(state = state, definition = def, spawnPos = state.pos)
                loaded++
            }
            log.info("Loaded {} NPCs from {}", loaded, savePath)
        }.onFailure { e -> log.warn("Failed to load NPCs from {}: {}", savePath, e.message) }
    }

    fun save(savePath: Path) {
        runCatching {
            savePath.parent?.createDirectories()
            val states = npcs.values.map { it.state }
            savePath.writeText(Json.encodeToString(ListSerializer(NpcState.serializer()), states))
        }.onFailure { e -> log.warn("Failed to save NPCs: {}", e.message) }
    }

    suspend fun spawnNpc(name: String, type: String, pos: Vec3): NpcInstance {
        val def = definitions[type] ?: error("Unknown NPC type: '$type'. Available: ${definitions.keys}")
        val id = UUID.randomUUID().toString()
        val state = NpcState(id = id, name = name, type = type, pos = pos, yaw = 0f)
        val instance = NpcInstance(state = state, definition = def, spawnPos = pos)
        npcs[id] = instance
        broadcast(ServerMessage.NpcSpawned(state))
        log.info("NPC spawned: {} ({}) at ({},{},{})", name, type, pos.x, pos.y, pos.z)
        return instance
    }

    suspend fun despawnNpc(id: String) {
        if (npcs.remove(id) != null) {
            broadcast(ServerMessage.NpcDespawned(id))
            log.info("NPC despawned: {}", id.take(8))
        }
    }

    suspend fun tick(world: WorldState) {
        val sessions = getSessions()
        val rangesq = NpcConstants.UPDATE_RANGE * NpcConstants.UPDATE_RANGE
        for (instance in npcs.values) {
            val pos = instance.state.pos
            val chunkPos = ChunkPos(
                Math.floorDiv(pos.x.toInt(), WorldConstants.CHUNK_SIZE),
                Math.floorDiv(pos.z.toInt(), WorldConstants.CHUNK_SIZE),
            )
            if (world.getChunkIfDiscovered(chunkPos) == null) continue
            val before = instance.state
            val changed = instance.definition.behavior.tick(instance, world)
            if (changed && instance.state != before) {
                val roundedState = instance.state.round1()
                val pos = roundedState.pos
                for (session in sessions) {
                    val dx = session.state.pos.x - pos.x
                    val dz = session.state.pos.z - pos.z
                    if (dx * dx + dz * dz <= rangesq) {
                        val playerStates = lastSentToPlayer.getOrPut(session.id) { ConcurrentHashMap() }
                        if (playerStates[instance.state.id] != roundedState) {
                            playerStates[instance.state.id] = roundedState
                            session.send(ServerMessage.NpcUpdate(roundedState))
                        }
                    }
                }
            }
        }
    }

    fun clearPlayer(sessionId: String) {
        lastSentToPlayer.remove(sessionId)
    }

    suspend fun handleInteract(session: PlayerSession, npcId: String) {
        val instance = npcs[npcId] ?: return
        instance.definition.behavior.onInteract(instance, session) { msg -> session.send(msg) }
    }

    suspend fun sendAllTo(session: PlayerSession) {
        log.info("sendAllTo {}: {} NPCs in memory", session.id.take(8), npcs.size)
        val playerStates = lastSentToPlayer.getOrPut(session.id) { ConcurrentHashMap() }
        for (instance in npcs.values) {
            val roundedState = instance.state.round1()
            playerStates[instance.state.id] = roundedState
            session.send(ServerMessage.NpcSpawned(roundedState))
        }
        log.info("sendAllTo {}: done", session.id.take(8))
    }

    fun getAll(): Collection<NpcInstance> = npcs.values

    fun countByType(type: String): Int = npcs.values.count { it.state.type == type }

    fun countByTypeInChunk(type: String, chunkPos: ChunkPos): Int {
        val minX = chunkPos.cx * WorldConstants.CHUNK_SIZE.toFloat()
        val maxX = minX + WorldConstants.CHUNK_SIZE
        val minZ = chunkPos.cz * WorldConstants.CHUNK_SIZE.toFloat()
        val maxZ = minZ + WorldConstants.CHUNK_SIZE
        return npcs.values.count { instance ->
            instance.state.type == type &&
                instance.state.pos.x >= minX && instance.state.pos.x < maxX &&
                instance.state.pos.z >= minZ && instance.state.pos.z < maxZ
        }
    }

    fun findByNameOrId(query: String): NpcInstance? =
        npcs[query] ?: npcs.values.firstOrNull { it.state.name.equals(query, ignoreCase = true) }

    fun getDefinitions(): Map<String, NpcDefinition> = definitions
}
