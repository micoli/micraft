package org.micoli.micraft.game.npc

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.ListSerializer
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(NpcManager::class.java)

private fun Float.round1() = (Math.round(this * 1) / 1f)

private fun NpcState.round1() =
    copy(
        pos = pos.copy(x = pos.x.round1(), y = pos.y.round1(), z = pos.z.round1()),
        yaw = yaw.round1(),
    )

class NpcManager(
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val getSessions: () -> Collection<PlayerSession> = { emptyList() },
    private val onNpcKilled: suspend (NpcInstance) -> Unit = {},
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
                instance.definition.let {}
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
                val states =
                    Yaml.default.decodeFromString(
                        ListSerializer(NpcState.serializer()), savePath.readText())
                var loaded = 0
                for (state in states) {
                    val def = definitions[state.type]
                    if (def == null) {
                        log.warn("Unknown NPC type '{}' in save file — skipped", state.type)
                        continue
                    }
                    val fixedState =
                        if (state.maxHp <= 0) state.copy(currentHp = def.hp, maxHp = def.hp)
                        else state
                    npcs[state.id] =
                        NpcInstance(
                            state = fixedState,
                            currentHp = fixedState.currentHp,
                            definition = def,
                            spawnPos = state.pos)
                    loaded++
                }
                log.info("Loaded {} NPCs from {}", loaded, savePath)
            }
            .onFailure { e -> log.warn("Failed to load NPCs from {}: {}", savePath, e.message) }
    }

    fun save(savePath: Path) {
        runCatching {
                savePath.parent?.createDirectories()
                val states = npcs.values.map { it.state }
                savePath.writeText(
                    Yaml.default.encodeToString(ListSerializer(NpcState.serializer()), states))
            }
            .onFailure { e -> log.warn("Failed to save NPCs: {}", e.message) }
    }

    suspend fun spawnNpc(name: String, type: String, pos: Vec3): NpcInstance {
        val def =
            definitions[type] ?: error("Unknown NPC type: '$type'. Available: ${definitions.keys}")
        val id = UUID.randomUUID().toString()
        val state =
            NpcState(
                id = id,
                name = name,
                type = type,
                pos = pos,
                yaw = 0f,
                currentHp = def.hp,
                maxHp = def.hp)
        val instance =
            NpcInstance(state = state, currentHp = def.hp, definition = def, spawnPos = pos)
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
            val chunkPos =
                ChunkPos(
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
                        val playerStates =
                            lastSentToPlayer.getOrPut(session.id) { ConcurrentHashMap() }
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
                instance.state.pos.x >= minX &&
                instance.state.pos.x < maxX &&
                instance.state.pos.z >= minZ &&
                instance.state.pos.z < maxZ
        }
    }

    fun findByNameOrId(query: String): NpcInstance? =
        npcs[query] ?: npcs.values.firstOrNull { it.state.name.equals(query, ignoreCase = true) }

    fun getDefinitions(): Map<String, NpcDefinition> = definitions

    fun getInstance(id: String): NpcInstance? = npcs[id]

    suspend fun applyDamage(npcId: String, damage: Int, attackerId: String) {
        val instance =
            npcs[npcId]
                ?: run {
                    log.warn("applyDamage: NPC {} not found", npcId.take(8))
                    return
                }
        val now = System.currentTimeMillis()
        instance.currentHp = (instance.currentHp - damage).coerceAtLeast(0)
        instance.lastDamagedAtMs = now
        if (instance.aggroTarget == null) instance.aggroTarget = attackerId
        instance.damageContributors[attackerId] =
            (instance.damageContributors[attackerId] ?: 0) + damage

        val newHp = instance.currentHp
        val maxHp = instance.definition.hp
        instance.state = instance.state.copy(currentHp = newHp, maxHp = maxHp)
        broadcast(ServerMessage.HealthUpdate(npcId, true, newHp, maxHp))

        if (newHp <= 0) {
            log.info("NPC {} killed", instance.state.name)
            onNpcKilled(instance)
            despawnNpc(npcId)
        }
    }

    suspend fun tickAggro(
        sessions: Collection<PlayerSession>,
        combatProcessor: CombatProcessor,
    ) {
        val now = System.currentTimeMillis()
        for (instance in npcs.values) {
            val def = instance.definition
            val aggroRangeSq = def.aggroRange * def.aggroRange
            val deaggroMs = (def.deaggroTimeSec * 1000).toLong()

            when (def.aggroMode) {
                AggroMode.PASSIVE -> {
                    if (instance.aggroTarget != null &&
                        now - instance.lastDamagedAtMs > deaggroMs) {
                        instance.aggroTarget = null
                    }
                }
                AggroMode.AGGRESSIVE -> {
                    if (instance.aggroTarget == null) {
                        val npcPos = instance.state.pos
                        val inRange =
                            sessions.firstOrNull { session ->
                                val dx = session.state.pos.x - npcPos.x
                                val dz = session.state.pos.z - npcPos.z
                                dx * dx + dz * dz <= aggroRangeSq
                            }
                        if (inRange != null) instance.aggroTarget = inRange.id
                    } else {
                        val target = sessions.find { it.id == instance.aggroTarget }
                        if (target == null) {
                            instance.aggroTarget = null
                        } else {
                            val npcPos = instance.state.pos
                            val dx = target.state.pos.x - npcPos.x
                            val dz = target.state.pos.z - npcPos.z
                            if (dx * dx + dz * dz > aggroRangeSq * 4 &&
                                now - instance.lastDamagedAtMs > deaggroMs) {
                                instance.aggroTarget = null
                            }
                        }
                    }
                }
            }

            val aggroTargetId = instance.aggroTarget ?: continue
            val targetSession = sessions.find { it.id == aggroTargetId } ?: continue
            combatProcessor.handleNpcAttack(instance, targetSession)
        }
    }
}
