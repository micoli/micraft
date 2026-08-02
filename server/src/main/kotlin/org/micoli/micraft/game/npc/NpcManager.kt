package org.micoli.micraft.game.npc

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.abs
import kotlinx.serialization.builtins.ListSerializer
import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.combat.AttackLevelDefinition
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.animal.AnimalInstanceData
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.npc.NpcDeathCause
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
    /**
     * Notified once per death, with the reason. This manager is the *only* emitter of a death: an
     * animal reaching its lifespan reports it through here rather than raising its own event, so a
     * single death can never be counted twice.
     */
    private val onNpcKilled: suspend (NpcInstance, NpcDeathCause) -> Unit = { _, _ -> },
    private val broadcastCombatLog: suspend (String) -> Unit = {},
    private val grantNpcKillXp: suspend (predator: NpcInstance, prey: NpcInstance) -> Unit =
        { _, _ ->
        },
    /**
     * Tick context in force for this world. Re-read on every use so `/reload` keeps working on the
     * live server; the world simulator passes a fixed context instead.
     */
    private val ctxOf: () -> NpcTickContext = { NpcTickContext.live },
    /**
     * Notified when an NPC is hurt by another NPC. Wired to the pack coordinator, as a lambda so
     * that the coordinator can depend on this manager without a cycle.
     */
    private var onNpcDamagedByNpc: (victim: NpcInstance, attacker: NpcInstance) -> Unit = { _, _ ->
    },
) {
    private val ctx: NpcTickContext
        get() = ctxOf()

    private val tuning: NpcTuning
        get() = ctxOf().tuning

    /**
     * Random source handed to a freshly spawned NPC. Drawn from the world's source at spawn time:
     * spawn order is deterministic, so each NPC gets a stable seed even though the NPC map is
     * iterated in an arbitrary order afterwards.
     */
    private fun childRandom(): kotlin.random.Random =
        kotlin.random.Random(ctxOf().random.nextLong())

    /**
     * Id for a freshly spawned NPC, drawn from the world's random source rather than
     * `UUID.randomUUID()`.
     *
     * The id is not just a label: [HibernationConfig.offsetFor] phases an NPC's sleep window off
     * it, and it decides the iteration order of the NPC map, which in turn decides which of two
     * grazers reaches a flower first. Taken from the global source, a seeded world was therefore
     * not reproducible at all — two runs of the same seed diverged, and the simulator's `seed`
     * setting was advertising a guarantee it could not keep. On the live server the source is
     * unseeded, so ids stay as random as before.
     */
    private fun nextNpcId(): String {
        val random = ctxOf().random
        return UUID(random.nextLong(), random.nextLong()).toString()
    }

    private val npcs = ConcurrentHashMap<String, NpcInstance>()
    @Volatile private var definitions: Map<String, NpcDefinition> = emptyMap()
    private var lastEffectTickMs = System.currentTimeMillis()
    private val lastSentToPlayer = ConcurrentHashMap<String, ConcurrentHashMap<String, NpcState>>()
    private val pendingRespawns = ConcurrentHashMap<String, MutableList<PendingRespawn>>()
    private val broadCastNpcPositions = false

    private val adminListeners = CopyOnWriteArrayList<suspend (String) -> Unit>()

    fun addAdminListener(listener: suspend (String) -> Unit) = adminListeners.add(listener)

    fun removeAdminListener(listener: suspend (String) -> Unit) = adminListeners.remove(listener)

    private suspend fun notifyAdmins(json: String) {
        for (l in adminListeners) {
            try {
                l(json)
            } catch (_: Exception) {}
        }
    }

    private fun String.adminJson() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

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
                            def.computeMaxHp(state.level).let {
                                state.copy(currentHp = it, maxHp = it)
                            }
                        else state
                    val animalData = fixedState.animalData?.let { AnimalInstanceData.fromState(it) }
                    npcs[state.id] =
                        NpcInstance(
                            state = fixedState,
                            currentHp = fixedState.currentHp,
                            definition = def,
                            spawnPos = state.pos,
                            instanceLevel = fixedState.level,
                            xp = fixedState.xp,
                            animalData = animalData,
                            tuning = tuning,
                            random = childRandom())
                    loaded++
                }
                log.info("Loaded {} NPCs from {}", loaded, savePath)
            }
            .onFailure { e -> log.warn("Failed to load NPCs from {}: {}", savePath, e.message) }
    }

    fun save(savePath: Path) {
        runCatching {
                savePath.parent?.createDirectories()
                val states =
                    npcs.values.map { instance ->
                        val ad = instance.animalData
                        if (ad != null) instance.state.copy(animalData = ad.toState())
                        else instance.state
                    }
                savePath.writeText(
                    Yaml.default.encodeToString(ListSerializer(NpcState.serializer()), states))
            }
            .onFailure { e -> log.warn("Failed to save NPCs: {}", e.message) }
    }

    suspend fun killNpcByAge(npcId: String, instance: NpcInstance, now: Long) {
        if (instance.isDead) return
        broadcastCombatLog("[m:${instance.state.name}] has died of old age.")
        onNpcKilled(instance, NpcDeathCause.OLD_AGE)
        markNpcDead(npcId, instance, now)
    }

    suspend fun killNpcByStarvation(npcId: String, instance: NpcInstance, now: Long) {
        if (instance.isDead) return
        broadcastCombatLog("[m:${instance.state.name}] has starved to death.")
        onNpcKilled(instance, NpcDeathCause.STARVATION)
        markNpcDead(npcId, instance, now)
    }

    suspend fun evolveAnimal(
        instance: NpcInstance,
        adultType: String,
        animalData: AnimalInstanceData
    ) {
        val adultDef =
            definitions[adultType]
                ?: run {
                    log.warn("evolveAnimal: unknown adultType '{}'", adultType)
                    return
                }
        val adultLevel = instance.instanceLevel
        val adultMaxHp = adultDef.computeMaxHp(adultLevel)
        val evolvedAnimal =
            AnimalInstanceData(
                gender = animalData.gender,
                ageGameDays = animalData.ageGameDays,
                hunger = animalData.hunger,
                gestationRemainingDays = null,
                lastReproductionDay = animalData.lastReproductionDay,
                parentIds = animalData.parentIds,
                stats = animalData.stats,
                motherLevel = 0,
            )
        val pos = instance.state.pos
        val name = instance.state.name.replace(Regex("(?i)baby\\s*"), "").trim()
        despawnNpc(instance.state.id)
        val adult = spawnNpc(name, adultType, pos, adultLevel, animalData = evolvedAnimal)
        adult.currentHp = adultMaxHp / 2
        adult.state = adult.state.copy(currentHp = adult.currentHp)
        log.debug("NPC {} evolved into {} lv{}", instance.state.name, adultType, adultLevel)
    }

    suspend fun spawnNpc(
        name: String,
        type: String,
        pos: Vec3,
        instanceLevel: Int = -1,
        /**
         * Animal record to carry over — a newborn's inherited stats, or an evolving baby's age and
         * hunger. Left null, a type with an `animal:` block gets a fresh one; passing it here
         * rather than overwriting afterwards keeps the spawn from burning random draws it would
         * discard, which would shift every later draw in a seeded run.
         */
        animalData: AnimalInstanceData? = null,
    ): NpcInstance {
        val def =
            definitions[type] ?: error("Unknown NPC type: '$type'. Available: ${definitions.keys}")
        val effectiveLevel = if (instanceLevel < 1) def.minLevel else instanceLevel
        val spawnMaxHp = def.computeMaxHp(effectiveLevel)
        val id = nextNpcId()
        val state =
            NpcState(
                id = id,
                name = name,
                type = type,
                pos = pos,
                yaw = 0f,
                currentHp = spawnMaxHp,
                maxHp = spawnMaxHp,
                level = effectiveLevel,
                scale = def.animalConfig?.scale ?: 1.0f,
            )
        val instance =
            NpcInstance(
                state = state,
                definition = def,
                spawnPos = pos,
                instanceLevel = effectiveLevel,
                tuning = tuning,
                random = childRandom())
        // Attached here rather than by each caller: a spawn that skipped this — a console spawn, a
        // persisted NPC, a manual arena spawn — produced an animal with no lifecycle record, so it
        // never aged, never got hungry and never reproduced. Immortal by omission.
        val animalCfg = def.animalConfig
        if (animalCfg != null) {
            val data =
                animalData
                    ?: AnimalInstanceData.initial(
                        lifespanDays = animalCfg.lifespanDays,
                        baseStats = animalCfg.baseStats,
                        statsVariance = animalCfg.statsVariance,
                        random = instance.random,
                    )
            instance.animalData = data
            instance.state = instance.state.copy(animalData = data.toState())
        }
        npcs[id] = instance
        if (broadCastNpcPositions) {
            broadcast(ServerMessage.NpcSpawned(state))
        } else {
            val stateWithAggro = state.copy(aggroTargetId = null)
            val rangesq = tuning.updateRange * tuning.updateRange
            for (s in getSessions()) {
                val dx = s.state.pos.x - pos.x
                val dz = s.state.pos.z - pos.z
                if (dx * dx + dz * dz <= rangesq) {
                    lastSentToPlayer.getOrPut(s.id) { ConcurrentHashMap() }[id] = stateWithAggro
                    s.send(ServerMessage.NpcSpawned(stateWithAggro))
                }
            }
        }
        notifyAdmins(
            """{"type":"npcSpawned","id":"$id","name":${name.adminJson()},"npcType":${type.adminJson()},"x":${pos.x},"y":${pos.y},"z":${pos.z},"yaw":0,"currentHp":$spawnMaxHp,"maxHp":$spawnMaxHp,"isDead":false}""")
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
            notifyAdmins("""{"type":"npcDespawned","id":"$id"}""")
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
        val now = System.currentTimeMillis()
        val rangesq = tuning.updateRange * tuning.updateRange
        for (instance in npcs.values) {
            if (instance.isDead) {
                if (now - instance.deathTimeMs >= DEATH_DESPAWN_DELAY_MS)
                    despawnNpc(instance.state.id)
                continue
            }
            val pos = instance.state.pos
            val chunkPos =
                ChunkPos(
                    Math.floorDiv(pos.x.toInt(), WorldConstants.CHUNK_SIZE),
                    Math.floorDiv(pos.z.toInt(), WorldConstants.CHUNK_SIZE),
                )
            if (world.getChunkIfDiscovered(chunkPos) == null) continue
            // Player target wins; an NPC target (pack hunt, retaliation) is the fallback. Left null
            // otherwise so the animal behaviour can install its prey/mate target.
            instance.chaseTargetPos =
                instance.aggroTarget?.let { targetId ->
                    sessions.find { it.id == targetId }?.state?.pos
                }
                    ?: instance.npcAggroTarget?.let { targetId ->
                        npcs[targetId]?.takeIf { !it.isDead }?.state?.pos
                    }
                    ?: instance.packRallyPos
            val before = instance.state
            val changed =
                instance.definition.behavior.tick(
                    instance, world, ctx.copy(random = instance.random))
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
                notifyAdmins(
                    """{"type":"npcUpdate","id":"${stateWithAggro.id}","x":${stateWithAggro.pos.x},"y":${stateWithAggro.pos.y},"z":${stateWithAggro.pos.z},"yaw":${stateWithAggro.yaw},"currentHp":${stateWithAggro.currentHp},"maxHp":${stateWithAggro.maxHp},"isDead":${instance.isDead}}""")
            }
        }
    }

    suspend fun tickVisibility(sessions: Collection<PlayerSession>) {
        val rangesq = tuning.updateRange * tuning.updateRange
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
        val zoneSizeSq = tuning.npcZoneSize.toFloat().let { it * it }
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
        val zx = Math.floorDiv(wx.toInt(), tuning.npcZoneSize)
        val zz = Math.floorDiv(wz.toInt(), tuning.npcZoneSize)
        return "$zx,$zz"
    }

    fun countInZone(zoneKey: String): Int {
        val parts = zoneKey.split(",")
        val zx = parts[0].toInt()
        val zz = parts[1].toInt()
        val minX = zx * tuning.npcZoneSize.toFloat()
        val maxX = minX + tuning.npcZoneSize
        val minZ = zz * tuning.npcZoneSize.toFloat()
        val maxZ = minZ + tuning.npcZoneSize
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
        instance.definition.behavior.onInteract(instance, session, ctx) { msg -> session.send(msg) }
    }

    suspend fun sendAllTo(session: PlayerSession) {
        log.info("sendAllTo {}: {} NPCs in memory", session.id.take(8), npcs.size)
        val rangesq = tuning.updateRange * tuning.updateRange
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

    /**
     * Living population per type, in one pass.
     *
     * The spawner needs a count for every type it considers, and [countByType] walks the whole map
     * each time — one grouping pass instead of one scan per type per spawn attempt.
     * Dead-but-not-yet despawned NPCs are excluded: a quota is about how many are alive, and
     * corpses linger for five seconds.
     */
    fun countsByType(): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (instance in npcs.values) {
            if (instance.isDead) continue
            val type = instance.state.type
            counts[type] = (counts[type] ?: 0) + 1
        }
        return counts
    }

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

    /** Late-bound because the pack coordinator is built from this manager. */
    fun setNpcDamagedByNpcHook(hook: (victim: NpcInstance, attacker: NpcInstance) -> Unit) {
        onNpcDamagedByNpc = hook
    }

    suspend fun applyDamage(npcId: String, damage: Int, attackerId: String) {
        val instance =
            npcs[npcId]
                ?: run {
                    log.warn("applyDamage: NPC {} not found", npcId.take(8))
                    return
                }
        if (instance.isDead) return
        val now = System.currentTimeMillis()
        instance.currentHp = (instance.currentHp - damage).coerceAtLeast(0)
        instance.lastDamagedAtMs = now
        if (instance.hibernating && instance.definition.hibernation?.wakeOnDamage == true) {
            instance.hibernating = false
            instance.hibernationWakeForced = true
            broadcastCombatLog("[m:${instance.state.name}] wakes up from its hibernation!")
        }
        val attackerNpcInstance = npcs[attackerId]
        if (attackerNpcInstance != null) {
            // Hurt by another NPC: a separate target field, otherwise tickAggro would drop it on
            // the next tick when the id fails to resolve to a session.
            if (instance.npcAggroTarget == null &&
                instance.aggroTarget == null &&
                !instance.hibernating) {
                instance.npcAggroTarget = attackerId
                broadcastCombatLog(
                    "[m:${instance.state.name}] turns on [m:${attackerNpcInstance.state.name}]!")
            }
            onNpcDamagedByNpc(instance, attackerNpcInstance)
        } else if (instance.aggroTarget == null && !instance.hibernating) {
            // A hibernating NPC that keeps sleeping through the hit retaliates against nobody.
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
        if (getSessions().any { it.id == attackerId }) {
            instance.damageContributors[attackerId] =
                (instance.damageContributors[attackerId] ?: 0) + damage
        }

        val newHp = instance.currentHp
        val maxHp = instance.maxHp
        instance.state = instance.state.copy(currentHp = newHp, maxHp = maxHp)
        broadcast(ServerMessage.HealthUpdate(npcId, true, newHp, maxHp))
        notifyAdmins(
            """{"type":"healthUpdate","id":"$npcId","currentHp":$newHp,"maxHp":$maxHp,"isDead":${newHp <= 0},"attackerId":"$attackerId"}""")

        if (newHp <= 0) {
            log.info("NPC {} killed", instance.state.name)
            broadcastCombatLog("[m:${instance.state.name}] has been slain!")
            if (getSessions().none { it.id == attackerId }) {
                val attackerNpc = npcs[attackerId]
                if (attackerNpc != null && !attackerNpc.isDead) {
                    grantNpcKillXp(attackerNpc, instance)
                    notifyAdmins(
                        """{"type":"npcXpUpdate","id":"${attackerNpc.state.id}","xp":${attackerNpc.xp},"level":${attackerNpc.instanceLevel}}""")
                }
            }
            onNpcKilled(instance, NpcDeathCause.KILLED)
            markNpcDead(npcId, instance, System.currentTimeMillis())
        }
    }

    fun applyStatusEffect(npcId: String, levelDef: AttackLevelDefinition, now: Long) {
        val instance = npcs[npcId] ?: return
        val effect = levelDef.statusEffect ?: return
        val durationSec = levelDef.durationSec ?: effect.durationSec
        applyStatusEffectDirectly(npcId, effect, durationSec, now)
    }

    fun applyStatusEffectDirectly(
        npcId: String,
        effect: StatusEffect,
        durationSec: Float,
        now: Long
    ) {
        val instance = npcs[npcId] ?: return
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
            if (instance.isDead) continue
            val effects = instance.activeEffects
            if (effects.isEmpty()) continue
            effects.removeAll { it.expiresAtMs <= now }
            var hpDelta = 0f
            for (active in effects) {
                when (active.effect) {
                    is StatusEffect.Pyre -> hpDelta -= 4f * dtSec
                    is StatusEffect.Withering -> hpDelta -= 3f * dtSec
                    is StatusEffect.Poisoned -> hpDelta -= 2f * dtSec
                    else -> {}
                }
            }
            if (hpDelta < 0) {
                instance.pendingDotDamage += -hpDelta
                val damage = instance.pendingDotDamage.toInt()
                if (damage > 0) {
                    instance.pendingDotDamage -= damage
                    instance.currentHp = (instance.currentHp - damage).coerceAtLeast(0)
                    val maxHp = instance.maxHp
                    instance.state =
                        instance.state.copy(currentHp = instance.currentHp, maxHp = maxHp)
                    broadcast(
                        ServerMessage.HealthUpdate(
                            instance.state.id, true, instance.currentHp, maxHp))
                    if (instance.currentHp <= 0) {
                        broadcastCombatLog("[m:${instance.state.name}] withers to death!")
                        onNpcKilled(instance, NpcDeathCause.KILLED)
                        markNpcDead(instance.state.id, instance, now)
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
            if (instance.isDead) continue
            if (instance.hibernating) {
                // Asleep: drops whatever it was after and acquires nothing new.
                if (instance.aggroTarget != null || instance.npcAggroTarget != null) {
                    instance.aggroTarget = null
                    instance.npcAggroTarget = null
                    broadcastAggroUpdate(instance, sessions)
                }
                continue
            }
            val def = instance.definition
            val aggroRangeSq = def.aggroRange * def.aggroRange
            val deaggroMs = (def.deaggroTimeSec * 1000).toLong()
            val prevAggroTarget = instance.aggroTarget

            if (instance.currentMana < instance.maxMana)
                instance.currentMana = (instance.currentMana + 1).coerceAtMost(instance.maxMana)
            if (instance.aggroTarget != null && instance.currentRage < instance.maxRage)
                instance.currentRage = (instance.currentRage + 2).coerceAtMost(instance.maxRage)
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
                                if (session.isDowned) return@firstOrNull false
                                val playerLevel = session.characterData?.level ?: 1
                                if (abs(playerLevel - instance.instanceLevel) > 5)
                                    return@firstOrNull false
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

            val aggroTargetId = instance.aggroTarget
            if (aggroTargetId == null) {
                tickNpcTarget(instance, now, deaggroMs, aggroRangeSq, combatProcessor)
                continue
            }
            val targetSession = sessions.find { it.id == aggroTargetId } ?: continue
            if (targetSession.isDowned) {
                instance.aggroTarget = null
                continue
            }
            combatProcessor.handleNpcAttack(instance, targetSession)
        }
    }

    /**
     * Chase and hit an NPC target — pack hunt or retaliation. Same leash rules as the player case:
     * given up past twice the aggro range once the NPC has been left alone long enough.
     */
    private suspend fun tickNpcTarget(
        instance: NpcInstance,
        now: Long,
        deaggroMs: Long,
        aggroRangeSq: Float,
        combatProcessor: CombatProcessor,
    ) {
        val targetId = instance.npcAggroTarget ?: return
        val target = npcs[targetId]
        if (target == null || target.isDead) {
            instance.npcAggroTarget = null
            return
        }
        val pos = instance.state.pos
        val dx = target.state.pos.x - pos.x
        val dy = target.state.pos.y - pos.y
        val dz = target.state.pos.z - pos.z
        // A pack member is allowed to follow its quarry as far as its pack leash, not just twice
        // its own aggro range — a wolf only sees 6 blocks but hunts a bear much further out.
        val giveUpSq =
            instance.definition.packConfig
                ?.takeIf { instance.packId != null }
                ?.let { it.chaseRadius * it.chaseRadius } ?: (aggroRangeSq * 4)
        if (dx * dx + dy * dy + dz * dz > giveUpSq && now - instance.lastDamagedAtMs > deaggroMs) {
            instance.npcAggroTarget = null
            broadcastCombatLog(
                "[m:${instance.state.name}] loses interest in [m:${target.state.name}].")
            return
        }
        combatProcessor.handleNpcAttackNpc(instance, target)
    }

    private suspend fun broadcastAggroUpdate(
        instance: NpcInstance,
        sessions: Collection<PlayerSession>,
    ) {
        val stateWithAggro = instance.state.round1().copy(aggroTargetId = instance.aggroTarget)
        val rangesq = tuning.updateRange * tuning.updateRange
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

    private suspend fun markNpcDead(npcId: String, instance: NpcInstance, now: Long) {
        instance.isDead = true
        instance.deathTimeMs = now
        instance.aggroTarget = null
        instance.activeEffects.clear()
        instance.state = instance.state.copy(isDead = true, currentHp = 0)
        getSessions()
            .filter { it.combatState.targetId == npcId && it.combatState.targetIsNpc }
            .forEach { session ->
                session.combatState = session.combatState.copy(targetId = null)
                session.send(ServerMessage.CombatTargetUpdate(null, null, 0, 0))
            }
        val targets = getSessions().filter { lastSentToPlayer[it.id]?.containsKey(npcId) == true }
        targets.forEach { it.send(ServerMessage.NpcUpdate(instance.state)) }
    }

    companion object {
        private const val DEATH_DESPAWN_DELAY_MS = 5_000L
    }
}
