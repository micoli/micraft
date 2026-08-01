package org.micoli.micraft.simulation

import java.nio.file.Path
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.game.GameTimeService
import org.micoli.micraft.game.TICK_MS
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.FantasyNameGenerator
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcSpawner
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.npc.NpcTickPipeline
import org.micoli.micraft.game.npc.NpcTuning
import org.micoli.micraft.game.npc.NpcYamlOverride
import org.micoli.micraft.game.npc.animal.AnimalEvent
import org.micoli.micraft.game.npc.animal.AnimalEventType
import org.micoli.micraft.game.npc.animal.AnimalInstanceData
import org.micoli.micraft.game.npc.animal.AnimalInteractionProcessor
import org.micoli.micraft.game.npc.applyOverride
import org.micoli.micraft.game.tick.MovementProcessor
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.FlatArenaChunkGenerator
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(WorldSimulator::class.java)

private val simJson = Json { ignoreUnknownKeys = true }

/**
 * Registries the simulator borrows from the live server so its rules are the real rules. Nothing is
 * loaded from disk here; the caller passes what the running world already holds.
 */
class SimulationDeps(
    val definitions: Map<String, NpcDefinition>,
    val combatConfig: CombatConfigData,
    val attackRegistry: Map<String, AttackDefinition>,
    val armorRegistry: Map<String, ArmorDefinition>,
    val classRegistry: Map<String, ClassDefinitionEntry>,
    val i18n: I18nConfig,
    val vegetationConfig: VegetationConfig,
)

/**
 * A disposable, fully in-memory world for observing NPC rules at high speed.
 *
 * Everything is instance-scoped: its own [WorldState] with no persistence, its own [NpcManager],
 * its own [NpcTuning] and definition overrides. The NPC tick order itself is *not* reimplemented —
 * it is delegated to [NpcTickPipeline], the same object the live game loop drives, so behaviour
 * cannot drift between the two.
 */
class WorldSimulator(
    val config: SimulationConfig,
    deps: SimulationDeps,
) {
    private val generator =
        FlatArenaChunkGenerator(
            halfSize = config.halfSize,
            groundY = config.groundY,
            wallHeight = config.wallHeight,
            maxNpcs = config.maxNpcs,
            zoneLevel = config.zoneLevel,
        )

    val world = WorldState(generator, persistence = null)

    private val gameTimeService = GameTimeService(config.gameDayDurationSeconds)

    @Volatile private var tuning: NpcTuning = config.npcTuning
    private val random = Random(config.seed)
    private val ctx: NpcTickContext
        get() = NpcTickContext(tuning, random)

    val events = SimEventLog(EVENT_HISTORY)

    private val sessions = mutableListOf<SimPlayerSession>()

    private val npcManager =
        NpcManager(
            broadcast = {},
            getSessions = { sessions.toList() },
            onNpcKilled = { instance ->
                logEvent(SimEventType.DEATH, "mort de ${instance.state.name}", instance)
            },
            broadcastCombatLog = { text -> logCombatLine(text) },
            ctxOf = { ctx },
        )

    private val npcSpawner = NpcSpawner()

    private val combatProcessor =
        CombatProcessor(
            config = deps.combatConfig,
            attackRegistry = deps.attackRegistry,
            armorRegistry = deps.armorRegistry,
            classRegistry = deps.classRegistry,
            npcManager = npcManager,
            getSessions = { sessions.toList() },
            broadcastCombatLog = { text -> logCombatLine(text) },
            subscribeToChannel = { _, _ -> },
            i18n = deps.i18n,
            savePlayer = {},
        )

    private val vegetationManager =
        VegetationManager(
            world,
            deps.vegetationConfig,
            // never saved: the simulated world is discarded on stop
            savePath = Path.of("data/world/.simulator/vegetation_state.yaml"),
        )

    private val animals =
        AnimalInteractionProcessor(
            npcManager = npcManager,
            combatProcessor = combatProcessor,
            world = world,
            vegetationManager = vegetationManager,
            gameTimeService = gameTimeService,
            broadcast = {},
            ctxOf = { ctx },
            onEvent = { event -> logAnimalEvent(event) },
        )

    private val pipeline =
        NpcTickPipeline(
            npcManager = npcManager,
            npcSpawner = npcSpawner,
            animals = animals,
            ctxOf = { ctx },
        )

    private val movementProcessor = MovementProcessor(world)

    @Volatile
    var tick: Long = 0L
        private set

    @Volatile
    var ticksPerSecond: Int = config.ticksPerSecond
        private set

    @Volatile private var realTps: Double = 0.0
    private var tpsWindowStartMs = System.currentTimeMillis()
    private var tpsWindowTicks = 0L

    private var scope: CoroutineScope? = null

    val paused: Boolean
        get() = ticksPerSecond <= 0

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Build the arena, apply definition overrides, place players and the initial NPC batch. */
    suspend fun start() {
        npcManager.loadDefinitions(overriddenDefinitions(config.npcDefinitionOverrides))
        pregenerateArena()
        npcManager.addAdminListener { json -> logNpcManagerEvent(json) }
        config.players.forEach { spec -> addPlayer(spec) }
        config.initialSpawns.forEach { spawn ->
            repeat(spawn.count) { spawnAtRandom(spawn.type, spawn.level) }
        }
        logEvent(
            SimEventType.SYSTEM,
            "arène ${config.halfSize * 2}×${config.halfSize * 2} prête — ${npcManager.getAll().size} NPC, ${sessions.size} joueur(s)")
        val loop = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { scope = it }
        loop.launch { runLoop() }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    private suspend fun runLoop() {
        val currentScope = scope ?: return
        while (currentScope.coroutineContext[Job]?.isActive == true) {
            val tps = ticksPerSecond
            if (tps <= 0) {
                delay(PAUSED_POLL_MS)
                recordTps(0)
                continue
            }
            // batch when very fast so we never schedule more than ~100 wakeups/second
            val batch = if (tps > MAX_WAKEUPS_PER_SECOND) tps / MAX_WAKEUPS_PER_SECOND else 1
            repeat(batch) { runCatching { step() }.onFailure { logStepFailure(it) } }
            recordTps(batch)
            delay(max(1L, 1000L * batch / tps))
        }
    }

    private fun logStepFailure(t: Throwable) {
        log.error("simulation step error: {}", t.message, t)
        logEvent(SimEventType.SYSTEM, "erreur de tick: ${t.message}")
    }

    /** Advance [count] ticks by hand; used by the step buttons while paused. */
    suspend fun stepOnce(count: Int = 1) {
        repeat(count.coerceIn(1, MAX_MANUAL_STEPS)) {
            runCatching { step() }.onFailure { logStepFailure(it) }
        }
    }

    /**
     * One tick, in the same order the live server uses. Player movement is the only thing done
     * here; everything NPC-related belongs to [NpcTickPipeline].
     */
    suspend fun step() {
        tick++
        for (session in sessions) {
            val newState = movementProcessor.process(session, session.takeInput())
            if (newState != session.state) session.state = newState
        }
        gameTimeService.tick(TICK_SECONDS.toDouble())
        pipeline.tick(world, sessions.toList(), combatProcessor)
        if (config.autoSpawnEnabled &&
            sessions.isNotEmpty() &&
            tick % LIFECYCLE_INTERVAL_TICKS == 0L) {
            // needs a player nearby, like the real spawner does
            pipeline.lifecycle(world, sessions.toList())
        }
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    fun setSpeed(tps: Int) {
        ticksPerSecond = tps.coerceIn(0, MAX_TPS)
        logEvent(
            SimEventType.SYSTEM,
            if (ticksPerSecond == 0) "simulation en pause" else "vitesse: $ticksPerSecond ticks/s")
    }

    fun setTuning(newTuning: NpcTuning) {
        tuning = newTuning
        logEvent(SimEventType.SYSTEM, "règles NPC mises à jour")
    }

    /** Live instances keep their old definition until they respawn, as on the live server. */
    fun applyDefinitionOverrides(overrides: Map<String, NpcYamlOverride>) {
        npcManager.loadDefinitions(overriddenDefinitions(overrides))
        logEvent(
            SimEventType.SYSTEM,
            "définitions surchargées (${overrides.size}) — actif au prochain spawn")
    }

    suspend fun spawn(type: String, x: Float, z: Float, count: Int = 1, level: Int? = null) {
        repeat(count.coerceIn(1, MAX_SPAWN_BATCH)) {
            val pos = Vec3(clamp(x), config.groundY + 1f, clamp(z))
            spawnAt(type, pos, level)
        }
    }

    /** Place one NPC with a chosen name — used by scripted setups and by the parity test. */
    suspend fun spawnNamed(
        name: String,
        type: String,
        pos: Vec3,
        level: Int? = null
    ): NpcInstance? {
        if (!npcManager.getDefinitions().containsKey(type)) {
            logEvent(SimEventType.SYSTEM, "type NPC inconnu: $type")
            return null
        }
        val instance = npcManager.spawnNpc(name, type, pos, level ?: config.zoneLevel)
        val animalCfg = instance.definition.animalConfig
        if (animalCfg != null && instance.animalData == null) {
            val data =
                AnimalInstanceData.initial(
                    lifespanDays = animalCfg.lifespanDays,
                    baseStats = animalCfg.baseStats,
                    statsVariance = animalCfg.statsVariance,
                )
            instance.animalData = data
            instance.state = instance.state.copy(animalData = data.toState())
        }
        return instance
    }

    /** Live NPC instances, for inspection and assertions. */
    fun npcInstances(): Collection<NpcInstance> = npcManager.getAll()

    fun applyPlayerInput(name: String, dx: Float, dz: Float, yaw: Float, jump: Boolean) {
        val session = sessions.find { it.userName == name } ?: return
        session.pendingInput =
            session.pendingInput.copy(dx = dx, dz = dz, yaw = yaw, jumpRequested = jump)
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    fun arenaDto() = SimArenaDto(config.halfSize, config.groundY, config.wallHeight)

    fun statsDto() =
        SimStatsDto(
            tick = tick,
            gameDay = gameTimeService.currentGameDay,
            configuredTps = ticksPerSecond,
            realTps = realTps,
            npcCount = npcManager.getAll().size,
            paused = paused,
        )

    fun npcDtos(): List<SimNpcDto> = npcManager.getAll().map { it.toDto() }

    fun playerDtos(): List<SimPlayerDto> =
        sessions.map {
            SimPlayerDto(
                id = it.id,
                name = it.userName,
                x = it.state.pos.x,
                y = it.state.pos.y,
                z = it.state.pos.z,
                yaw = it.state.orientation.yaw,
            )
        }

    fun npcDetail(npcId: String): SimNpcDetailDto? {
        val instance = npcManager.getInstance(npcId) ?: return null
        val def = instance.definition
        val animal = instance.animalData
        return SimNpcDetailDto(
            npc = instance.toDto(),
            behaviorKey = def.behaviorKey,
            aggroMode = def.aggroMode.name,
            characterClass = def.characterClass.name,
            xp = instance.xp,
            currentMana = instance.currentMana,
            maxMana = instance.maxMana,
            width = def.width,
            height = def.height,
            wanderSpeed = def.wanderSpeed,
            wanderRadius = def.wanderRadius,
            aggroRange = def.aggroRange,
            attacks = def.attacks.map { "${it.attackId} lv${it.level}" },
            spells = def.spells,
            baseStats = animal?.stats ?: def.baseStats,
            wanderPhase = instance.wanderPhase.toString(),
            spawnX = instance.spawnPos.x,
            spawnZ = instance.spawnPos.z,
            parentIds = animal?.parentIds?.toList() ?: emptyList(),
            preyTargetId = animal?.preyTargetId,
            mateTargetId = animal?.mateTargetId,
            diet = def.animalConfig?.diet?.name,
            activeEffects = instance.activeEffects.map { it.effect::class.simpleName ?: "?" },
        )
    }

    fun availableTypes(): List<String> = npcManager.getDefinitions().keys.sorted()

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun overriddenDefinitions(
        overrides: Map<String, NpcYamlOverride>
    ): Map<String, NpcDefinition> =
        baseDefinitions.mapValues { (type, def) ->
            overrides[type]?.let { def.applyOverride(it) } ?: def
        }

    private val baseDefinitions: Map<String, NpcDefinition> = deps.definitions

    private fun pregenerateArena() {
        val chunkRadius = config.halfSize / WorldConstants.CHUNK_SIZE + 1
        for (cx in -chunkRadius..chunkRadius) for (cz in -chunkRadius..chunkRadius) {
            world.getOrGenerate(ChunkPos(cx, cz))
        }
        log.info(
            "Simulator arena pregenerated: {} chunks, halfSize={}",
            world.loadedChunkCount(),
            config.halfSize)
    }

    private fun addPlayer(spec: SimPlayerSpec) {
        val id = UUID.randomUUID().toString()
        val state =
            PlayerState(
                id = id,
                name = spec.name,
                pos = Vec3(clamp(spec.x), config.groundY + 1f, clamp(spec.z)),
                orientation = Orientation(0f, 0f),
            )
        sessions.add(SimPlayerSession(id = id, userName = spec.name, state = state))
    }

    private suspend fun spawnAtRandom(type: String, level: Int?) {
        val range = config.halfSize - 2
        val x = (random.nextInt(-range, range)).toFloat() + 0.5f
        val z = (random.nextInt(-range, range)).toFloat() + 0.5f
        spawnAt(type, Vec3(x, config.groundY + 1f, z), level)
    }

    private suspend fun spawnAt(type: String, pos: Vec3, level: Int?) {
        val label = type.replace('_', ' ').replaceFirstChar { it.uppercase() }
        spawnNamed("$label - ${FantasyNameGenerator.generate(type)}", type, pos, level)
    }

    private fun clamp(v: Float): Float =
        v.coerceIn(-(config.halfSize - 2).toFloat(), (config.halfSize - 2).toFloat())

    private fun recordTps(ticksDone: Int) {
        tpsWindowTicks += ticksDone
        val now = System.currentTimeMillis()
        val elapsed = now - tpsWindowStartMs
        if (elapsed >= TPS_WINDOW_MS) {
            realTps = tpsWindowTicks * 1000.0 / elapsed
            tpsWindowTicks = 0
            tpsWindowStartMs = now
        }
    }

    private fun NpcInstance.toDto(): SimNpcDto {
        val animal = animalData
        return SimNpcDto(
            id = state.id,
            name = state.name,
            type = state.type,
            x = state.pos.x,
            y = state.pos.y,
            z = state.pos.z,
            yaw = state.yaw,
            currentHp = currentHp,
            maxHp = maxHp,
            level = instanceLevel,
            isDead = isDead,
            aggroTargetId = aggroTarget,
            gender = animal?.gender?.name,
            hunger = animal?.hunger,
            gestationRemainingDays = animal?.gestationRemainingDays,
            ageGameDays = animal?.ageGameDays,
        )
    }

    // ── Event plumbing ────────────────────────────────────────────────────────

    private fun logEvent(
        type: SimEventType,
        message: String,
        instance: NpcInstance? = null,
        other: NpcInstance? = null,
        value: Double? = null,
    ) {
        events.add(
            SimEvent(
                seq = 0L,
                tick = tick,
                gameDay = gameTimeService.currentGameDay,
                type = type,
                message = message,
                npcId = instance?.state?.id,
                npcName = instance?.state?.name,
                npcType = instance?.state?.type,
                otherId = other?.state?.id,
                otherName = other?.state?.name,
                value = value,
            ))
    }

    private fun logAnimalEvent(event: AnimalEvent) {
        val who = event.npcName
        val other = event.otherName
        val type =
            when (event.type) {
                AnimalEventType.HUNGRY -> SimEventType.HUNGRY
                AnimalEventType.FED -> SimEventType.FED
                AnimalEventType.MATING -> SimEventType.MATING
                AnimalEventType.GESTATION_START -> SimEventType.GESTATION_START
                AnimalEventType.BIRTH -> SimEventType.BIRTH
                AnimalEventType.EVOLVE -> SimEventType.EVOLVE
                AnimalEventType.AGE_DEATH -> SimEventType.AGE_DEATH
            }
        val message =
            when (event.type) {
                AnimalEventType.HUNGRY -> "$who a faim (${event.value.pct()})"
                AnimalEventType.FED ->
                    if (other != null) "$who a mangé $other (satiété ${event.value.satiety()})"
                    else "$who a brouté (satiété ${event.value.satiety()})"
                AnimalEventType.MATING -> "$who s'accouple avec $other"
                AnimalEventType.GESTATION_START ->
                    "$who enceinte de $other — ${event.value.days()} jours"
                AnimalEventType.BIRTH ->
                    "naissance de $who (mère $other, niveau ${event.value.int()})"
                AnimalEventType.EVOLVE -> "$who devient adulte (niveau ${event.value.int()})"
                AnimalEventType.AGE_DEATH ->
                    "$who meurt de vieillesse (${event.value.days()} jours)"
            }
        events.add(
            SimEvent(
                seq = 0L,
                tick = tick,
                gameDay = gameTimeService.currentGameDay,
                type = type,
                message = message,
                npcId = event.npcId,
                npcName = event.npcName,
                npcType = event.npcType,
                otherId = event.otherId,
                otherName = event.otherName,
                value = event.value,
            ))
    }

    /**
     * Combat-log lines are chat strings with `[m:NAME]` / `[p:NAME]` markers. Deaths and old age
     * already arrive as typed events (kill hook, animal hook), so those lines are dropped here
     * rather than logged twice.
     */
    private fun logCombatLine(text: String) {
        val clean = text.replace(Regex("\\[[mp]:([^]]*)]"), "$1")
        val type =
            when {
                clean.contains("old age") ||
                    clean.contains("slain") ||
                    clean.contains("withers to death") -> return
                clean.contains("targets") -> SimEventType.AGGRO_GAIN
                clean.contains("loses interest") -> SimEventType.AGGRO_LOST
                else -> SimEventType.ATTACK
            }
        logEvent(type, clean)
    }

    /** The admin listener emits hand-built JSON; only spawn/despawn/health matter here. */
    private fun logNpcManagerEvent(json: String) {
        val obj = runCatching { simJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return
        val kind = obj["type"]?.jsonPrimitive?.content ?: return
        val id = obj["id"]?.jsonPrimitive?.content
        val name = obj["name"]?.jsonPrimitive?.content
        when (kind) {
            "npcSpawned" ->
                events.add(baseEvent(SimEventType.SPAWN, "apparition de ${name ?: id}", id, name))
            "npcDespawned" ->
                events.add(
                    baseEvent(SimEventType.DESPAWN, "disparition de ${id?.take(8)}", id, name))
            "healthUpdate" -> {
                val hp = obj["currentHp"]?.jsonPrimitive?.content
                val maxHp = obj["maxHp"]?.jsonPrimitive?.content
                val target = npcManager.getInstance(id ?: "")
                events.add(
                    baseEvent(
                        SimEventType.DAMAGE,
                        "${target?.state?.name ?: id?.take(8)} — $hp/$maxHp pv",
                        id,
                        target?.state?.name))
            }
        }
    }

    private fun baseEvent(
        type: SimEventType,
        message: String,
        npcId: String?,
        npcName: String?,
    ) =
        SimEvent(
            seq = 0L,
            tick = tick,
            gameDay = gameTimeService.currentGameDay,
            type = type,
            message = message,
            npcId = npcId,
            npcName = npcName,
        )

    companion object {
        const val EVENT_HISTORY = 300
        private const val MAX_TPS = 5_000
        private const val MAX_WAKEUPS_PER_SECOND = 100
        private const val PAUSED_POLL_MS = 50L
        private const val TPS_WINDOW_MS = 500L
        private const val MAX_MANUAL_STEPS = 10_000
        private const val MAX_SPAWN_BATCH = 200
        private val LIFECYCLE_INTERVAL_TICKS = max(1L, 5_000L / TICK_MS)
    }
}

private fun Double?.pct(): String = this?.let { "${(it * 100).toInt()}%" } ?: "?"

private fun Double?.satiety(): String = this?.let { "${((1 - it) * 100).toInt()}%" } ?: "?"

private fun Double?.days(): String = this?.let { String.format("%.2f", it) } ?: "?"

private fun Double?.int(): String = this?.toInt()?.toString() ?: "?"
