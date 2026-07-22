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
import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.combat.AttackLevelDefinition
import org.micoli.micraft.combat.StatusEffect
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

data class PendingRespawn(
    val name: String,
    val type: String,
    val spawnPos: Vec3,
    val instanceLevel: Int,
)

class NpcManager(
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val getSessions: () -> Collection<PlayerSession> = { emptyList() },
    private val onNpcKilled: suspend (NpcInstance) -> Unit = {},
    private val broadcastCombatLog: suspend (String) -> Unit = {},
) {
    private val npcs = ConcurrentHashMap<String, NpcInstance>()
    @Volatile private var definitions: Map<String, NpcDefinition> = emptyMap()
    private var lastEffectTickMs = System.currentTimeMillis()
    private val lastSentToPlayer = ConcurrentHashMap<String, ConcurrentHashMap<String, NpcState>>()
    private val pendingRespawns = ConcurrentHashMap<String, MutableList<PendingRespawn>>()
    private val broadCastNpcPositions = false

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
                        if (state.maxHp <= 0)
                            NpcHpCalculator.computeMaxHp(def, state.level).let {
                                state.copy(currentHp = it, maxHp = it)
                            }
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

    suspend fun spawnNpc(
        name: String,
        type: String,
        pos: Vec3,
        instanceLevel: Int = -1
    ): NpcInstance {
        val def =
            definitions[type] ?: error("Unknown NPC type: '$type'. Available: ${definitions.keys}")
        val effectiveLevel = if (instanceLevel < 1) def.minLevel else instanceLevel
        val spawnMaxHp = NpcHpCalculator.computeMaxHp(def, effectiveLevel)
        val id = UUID.randomUUID().toString()
        val state =
            NpcState(
                id = id,
                name = name,
                type = type,
                pos = pos,
                yaw = 0f,
                currentHp = spawnMaxHp,
                maxHp = spawnMaxHp,
                level = effectiveLevel)
        val instance =
            NpcInstance(
                state = state,
                currentHp = def.hp,
                definition = def,
                spawnPos = pos,
                instanceLevel = effectiveLevel)
        npcs[id] = instance
        if (broadCastNpcPositions) {
            broadcast(ServerMessage.NpcSpawned(state))
        } else {
            val stateWithAggro = state.copy(aggroTargetId = null)
            val rangesq = NpcConstants.UPDATE_RANGE * NpcConstants.UPDATE_RANGE
            for (s in getSessions()) {
                val dx = s.state.pos.x - pos.x
                val dz = s.state.pos.z - pos.z
                if (dx * dx + dz * dz <= rangesq) {
                    lastSentToPlayer.getOrPut(s.id) { ConcurrentHashMap() }[id] = stateWithAggro
                    s.send(ServerMessage.NpcSpawned(stateWithAggro))
                }
            }
        }
        log.debug(
            "NPC spawned: {} ({}) lv{} at ({},{},{})",
            name,
            type,
            effectiveLevel,
            pos.x,
            pos.y,
            pos.z)
        return instance
    }

    suspend fun despawnNpc(id: String) {
        if (npcs.remove(id) != null) {
            val targets = getSessions().filter { lastSentToPlayer[it.id]?.containsKey(id) == true }
            lastSentToPlayer.values.forEach { it.remove(id) }
            targets.forEach { it.send(ServerMessage.NpcDespawned(id)) }
            getSessions()
                .filter { it.combatState.targetId == id && it.combatState.targetIsNpc }
                .forEach { session ->
                    session.combatState = session.combatState.copy(targetId = null)
                    session.send(ServerMessage.CombatTargetUpdate(null, null, 0, 0))
                }
            log.debug("NPC despawned: {}", id.take(8))
        }
    }

    suspend fun tick(world: WorldState) {
        tickEffects()
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
            instance.chaseTargetPos =
                instance.aggroTarget?.let { targetId ->
                    sessions.find { it.id == targetId }?.state?.pos
                }
            val before = instance.state
            val changed = instance.definition.behavior.tick(instance, world)
            if (changed && instance.state != before) {
                val roundedState = instance.state.round1()
                val stateWithAggro = roundedState.copy(aggroTargetId = instance.aggroTarget)
                val pos = roundedState.pos
                for (session in sessions) {
                    val dx = session.state.pos.x - pos.x
                    val dz = session.state.pos.z - pos.z
                    if (dx * dx + dz * dz <= rangesq) {
                        val playerStates =
                            lastSentToPlayer.getOrPut(session.id) { ConcurrentHashMap() }
                        if (playerStates[instance.state.id] != stateWithAggro) {
                            playerStates[instance.state.id] = stateWithAggro
                            session.send(ServerMessage.NpcUpdate(stateWithAggro))
                        }
                    }
                }
            }
        }
    }

    suspend fun tickVisibility(sessions: Collection<PlayerSession>) {
        val rangesq = NpcConstants.UPDATE_RANGE * NpcConstants.UPDATE_RANGE
        for (session in sessions) {
            val playerPos = session.state.pos
            val known = lastSentToPlayer.getOrPut(session.id) { ConcurrentHashMap() }

            for (instance in npcs.values) {
                if (known.containsKey(instance.state.id)) continue
                val dx = playerPos.x - instance.state.pos.x
                val dz = playerPos.z - instance.state.pos.z
                if (dx * dx + dz * dz <= rangesq) {
                    val state = instance.state.round1().copy(aggroTargetId = instance.aggroTarget)
                    known[instance.state.id] = state
                    session.send(ServerMessage.NpcSpawned(state))
                }
            }

            val toRemove =
                known.keys.filter { npcId ->
                    val npc = npcs[npcId] ?: return@filter true
                    val dx = playerPos.x - npc.state.pos.x
                    val dz = playerPos.z - npc.state.pos.z
                    dx * dx + dz * dz > rangesq
                }
            toRemove.forEach { npcId ->
                known.remove(npcId)
                session.send(ServerMessage.NpcDespawned(npcId))
            }
        }
    }

    suspend fun despawnOrphanedNpcs(sessions: Collection<PlayerSession>) {
        val zoneSizeSq = NpcConstants.NPC_ZONE_SIZE.toFloat().let { it * it }
        val orphans =
            npcs.values.filter { npc ->
                sessions.none { s ->
                    val dx = s.state.pos.x - npc.state.pos.x
                    val dz = s.state.pos.z - npc.state.pos.z
                    dx * dx + dz * dz <= zoneSizeSq
                }
            }
        for (npc in orphans) {
            val key = zoneKey(npc.state.pos.x, npc.state.pos.z)
            pendingRespawns
                .getOrPut(key) { mutableListOf() }
                .add(
                    PendingRespawn(npc.state.name, npc.state.type, npc.spawnPos, npc.instanceLevel))
            despawnNpc(npc.state.id)
        }
        if (orphans.isNotEmpty()) log.info("Orphan-despawned {} NPCs", orphans.size)
    }

    suspend fun respawnPendingInZone(zoneX: Int, zoneZ: Int) {
        val key = "$zoneX,$zoneZ"
        val pending = pendingRespawns.remove(key) ?: return
        for (entry in pending) {
            if (definitions.containsKey(entry.type))
                spawnNpc(entry.name, entry.type, entry.spawnPos, entry.instanceLevel)
        }
        if (pending.isNotEmpty())
            log.info("Respawned {} pending NPCs in zone {}", pending.size, key)
    }

    fun zoneKey(wx: Float, wz: Float): String {
        val zx = Math.floorDiv(wx.toInt(), NpcConstants.NPC_ZONE_SIZE)
        val zz = Math.floorDiv(wz.toInt(), NpcConstants.NPC_ZONE_SIZE)
        return "$zx,$zz"
    }

    fun countInZone(zoneKey: String): Int {
        val parts = zoneKey.split(",")
        val zx = parts[0].toInt()
        val zz = parts[1].toInt()
        val minX = zx * NpcConstants.NPC_ZONE_SIZE.toFloat()
        val maxX = minX + NpcConstants.NPC_ZONE_SIZE
        val minZ = zz * NpcConstants.NPC_ZONE_SIZE.toFloat()
        val maxZ = minZ + NpcConstants.NPC_ZONE_SIZE
        return npcs.values.count {
            it.state.pos.x >= minX &&
                it.state.pos.x < maxX &&
                it.state.pos.z >= minZ &&
                it.state.pos.z < maxZ
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
        val rangesq = NpcConstants.UPDATE_RANGE * NpcConstants.UPDATE_RANGE
        val playerPos = session.state.pos
        val playerStates = lastSentToPlayer.getOrPut(session.id) { ConcurrentHashMap() }
        var sent = 0
        for (instance in npcs.values) {
            val dx = playerPos.x - instance.state.pos.x
            val dz = playerPos.z - instance.state.pos.z
            if (dx * dx + dz * dz > rangesq) continue
            val roundedState = instance.state.round1()
            val stateWithAggro = roundedState.copy(aggroTargetId = instance.aggroTarget)
            playerStates[instance.state.id] = stateWithAggro
            session.send(ServerMessage.NpcSpawned(stateWithAggro))
            sent++
        }
        log.info("sendAllTo {}: sent {} in-range NPCs", session.id.take(8), sent)
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
        if (instance.aggroTarget == null) {
            instance.aggroTarget = attackerId
            val attackerName = getSessions().find { it.id == attackerId }?.state?.name ?: "?"
            broadcastCombatLog("[m:${instance.state.name}] targets [p:$attackerName]!")
            if (instance.definition.aggroMode == AggroMode.PASSIVE_COOPERATIVE) {
                val npcPos = instance.state.pos
                val rangeSq = instance.definition.aggroRange * instance.definition.aggroRange
                npcs.values.forEach { peer ->
                    if (peer.state.id != npcId &&
                        peer.state.type == instance.state.type &&
                        peer.aggroTarget == null) {
                        val dx = peer.state.pos.x - npcPos.x
                        val dz = peer.state.pos.z - npcPos.z
                        if (dx * dx + dz * dz <= rangeSq) {
                            peer.aggroTarget = attackerId
                            broadcastCombatLog("[m:${peer.state.name}] targets [p:$attackerName]!")
                        }
                    }
                }
            }
        }
        instance.damageContributors[attackerId] =
            (instance.damageContributors[attackerId] ?: 0) + damage

        val newHp = instance.currentHp
        val maxHp = NpcHpCalculator.computeMaxHp(instance.definition, instance.instanceLevel)
        instance.state = instance.state.copy(currentHp = newHp, maxHp = maxHp)
        broadcast(ServerMessage.HealthUpdate(npcId, true, newHp, maxHp))

        if (newHp <= 0) {
            log.info("NPC {} killed", instance.state.name)
            broadcastCombatLog("[m:${instance.state.name}] has been slain!")
            onNpcKilled(instance)
            despawnNpc(npcId)
        }
    }

    fun applyStatusEffect(npcId: String, levelDef: AttackLevelDefinition, now: Long) {
        val instance = npcs[npcId] ?: return
        val effect = levelDef.statusEffect ?: return
        val durationSec = levelDef.durationSec ?: effect.durationSec
        val expiry = now + (durationSec * 1000).toLong()
        val idx = instance.activeEffects.indexOfFirst { it.effect::class == effect::class }
        if (idx >= 0) instance.activeEffects[idx] = ActiveStatusEffect(effect, expiry)
        else instance.activeEffects.add(ActiveStatusEffect(effect, expiry))
    }

    private suspend fun tickEffects() {
        val now = System.currentTimeMillis()
        val dtSec = (now - lastEffectTickMs) / 1000f
        lastEffectTickMs = now
        for (instance in npcs.values.toList()) {
            val effects = instance.activeEffects
            if (effects.isEmpty()) continue
            effects.removeAll { it.expiresAtMs <= now }
            var hpDelta = 0f
            for (active in effects) {
                when (active.effect) {
                    is StatusEffect.Pyre -> hpDelta -= 4f * dtSec
                    else -> {}
                }
            }
            if (hpDelta < 0) {
                instance.pendingDotDamage += -hpDelta
                val damage = instance.pendingDotDamage.toInt()
                if (damage > 0) {
                    instance.pendingDotDamage -= damage
                    instance.currentHp = (instance.currentHp - damage).coerceAtLeast(0)
                    val maxHp =
                        NpcHpCalculator.computeMaxHp(instance.definition, instance.instanceLevel)
                    instance.state =
                        instance.state.copy(currentHp = instance.currentHp, maxHp = maxHp)
                    broadcast(
                        ServerMessage.HealthUpdate(
                            instance.state.id, true, instance.currentHp, maxHp))
                    if (instance.currentHp <= 0) {
                        broadcastCombatLog("[m:${instance.state.name}] burns to death!")
                        onNpcKilled(instance)
                        despawnNpc(instance.state.id)
                    }
                }
            }
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
            val prevAggroTarget = instance.aggroTarget

            if (instance.currentMana < def.maxMana)
                instance.currentMana = (instance.currentMana + 1).coerceAtMost(def.maxMana)
            if (instance.aggroTarget != null && instance.currentRage < def.maxRage)
                instance.currentRage = (instance.currentRage + 2).coerceAtMost(def.maxRage)
            else if (instance.aggroTarget == null && instance.currentRage > 0)
                instance.currentRage = (instance.currentRage - 1).coerceAtLeast(0)

            when (def.aggroMode) {
                AggroMode.PASSIVE -> {
                    if (instance.aggroTarget != null &&
                        now - instance.lastDamagedAtMs > deaggroMs) {
                        instance.aggroTarget = null
                    }
                }
                AggroMode.PASSIVE_COOPERATIVE -> {
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
                                val dy = session.state.pos.y - npcPos.y
                                val dz = session.state.pos.z - npcPos.z
                                (dx * dx + dz * dz <= aggroRangeSq) && Math.abs(dy) <= 5
                            }
                        if (inRange != null) instance.aggroTarget = inRange.id
                    } else {
                        val target = sessions.find { it.id == instance.aggroTarget }
                        if (target == null) {
                            instance.aggroTarget = null
                        } else {
                            val npcPos = instance.state.pos
                            val dx = target.state.pos.x - npcPos.x
                            val dy = target.state.pos.y - npcPos.y
                            val dz = target.state.pos.z - npcPos.z
                            if (dx * dx + dy * dy + dz * dz > aggroRangeSq * 4 &&
                                now - instance.lastDamagedAtMs > deaggroMs) {
                                instance.aggroTarget = null
                            }
                        }
                    }
                }
            }

            val newAggroTarget = instance.aggroTarget
            if (prevAggroTarget == null && newAggroTarget != null) {
                val name = sessions.find { it.id == newAggroTarget }?.state?.name ?: "?"
                broadcastCombatLog("[m:${instance.state.name}] targets [p:$name]!")
                broadcastAggroUpdate(instance, sessions)
            } else if (prevAggroTarget != null && newAggroTarget == null) {
                val prevSession = sessions.find { it.id == prevAggroTarget }
                if (prevSession != null) {
                    broadcastCombatLog(
                        "[m:${instance.state.name}] loses interest in [p:${prevSession.state.name}].")
                }
                broadcastAggroUpdate(instance, sessions)
            }

            val aggroTargetId = instance.aggroTarget ?: continue
            val targetSession = sessions.find { it.id == aggroTargetId } ?: continue
            if (targetSession.isDowned) {
                instance.aggroTarget = null
                continue
            }
            combatProcessor.handleNpcAttack(instance, targetSession)
        }
    }

    private suspend fun broadcastAggroUpdate(
        instance: NpcInstance,
        sessions: Collection<PlayerSession>,
    ) {
        val stateWithAggro = instance.state.round1().copy(aggroTargetId = instance.aggroTarget)
        val rangesq = NpcConstants.UPDATE_RANGE * NpcConstants.UPDATE_RANGE
        val pos = stateWithAggro.pos
        for (session in sessions) {
            val dx = session.state.pos.x - pos.x
            val dz = session.state.pos.z - pos.z
            if (dx * dx + dz * dz <= rangesq) {
                val playerStates = lastSentToPlayer.getOrPut(session.id) { ConcurrentHashMap() }
                playerStates[instance.state.id] = stateWithAggro
                session.send(ServerMessage.NpcUpdate(stateWithAggro))
            }
        }
    }
}
