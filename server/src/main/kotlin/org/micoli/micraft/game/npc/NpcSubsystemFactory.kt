package org.micoli.micraft.game.npc

import org.micoli.micraft.game.GameTimeService
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.animal.AnimalEvent
import org.micoli.micraft.game.npc.animal.AnimalInteractionProcessor
import org.micoli.micraft.game.npc.pack.PackCoordinator
import org.micoli.micraft.game.npc.pack.PackEvent
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.npc.NpcDeathCause
import org.micoli.micraft.protocol.ServerMessage

/**
 * Everything the NPC subsystem needs from its host.
 *
 * Grouped into one object on purpose. The live server, the admin world simulator and the parity
 * test used to wire these hooks each on their own, and every hook one of them forgot became an
 * invisible behaviour difference — the simulator never granted kill XP, so NPCs never levelled, so
 * no baby ever grew up, and 60 simulated days of "balance data" described a world the game does not
 * have.
 *
 * Defaults are the *inert* choice, never a plausible-looking one: a host that means to stay silent
 * says nothing, and a host that forgets a hook gets no behaviour rather than someone else's.
 */
data class NpcSubsystemHooks(
    /** Network fan-out for NPC state. */
    val broadcast: suspend (ServerMessage) -> Unit = {},
    /**
     * Fan-out for world changes the animals cause (a grazed flower). Separate from [broadcast]: the
     * simulator drops NPC states but still needs to know the vegetation moved.
     */
    val broadcastWorldUpdate: suspend (ServerMessage) -> Unit = {},
    val getSessions: () -> Collection<PlayerSession> = { emptyList() },
    val broadcastCombatLog: suspend (String) -> Unit = {},
    /** One call per death, cause and killer (null for non-combat deaths) included. */
    val onNpcKilled: suspend (NpcInstance, NpcDeathCause, NpcInstance?) -> Unit = { _, _, _ -> },
    /** XP to a predator NPC for killing another NPC — what lets an NPC gain levels at all. */
    val grantNpcKillXp: suspend (predator: NpcInstance, prey: NpcInstance) -> Unit = { _, _ -> },
    /** Tick context (tuning + RNG). Re-read on every use so `/reload` keeps working. */
    val ctxOf: () -> NpcTickContext = { NpcTickContext.live },
    /**
     * Veto on creating life, births included. Always true on the live server; the simulator refuses
     * past its population ceiling.
     */
    val canSpawn: () -> Boolean = { true },
    val onAnimalEvent: (AnimalEvent) -> Unit = {},
    val onPackEvent: (PackEvent) -> Unit = {},
)

/** The wired NPC subsystem. Held together so a host cannot keep half of it. */
class NpcSubsystem(
    val npcManager: NpcManager,
    val npcSpawner: NpcSpawner,
    val gameTimeService: GameTimeService,
    val animals: AnimalInteractionProcessor,
    val packs: PackCoordinator,
    val hibernation: HibernationProcessor,
    val pipeline: NpcTickPipeline,
)

/**
 * The one place the NPC subsystem is wired.
 *
 * Built in two steps because [CombatProcessor] needs the [npcManager] and the animal processor
 * needs the combat processor. Rather than hide that cycle behind a lazy reference, the factory
 * exposes the manager first and [build] finishes the job — the dependency stays visible, and both
 * hosts still share one wiring.
 *
 * `NpcTickPipeline` owns the tick *order*; this owns the *wiring*. Both are needed for the
 * simulator to reproduce the game.
 */
class NpcSubsystemFactory(
    private val hooks: NpcSubsystemHooks,
    private val world: WorldState,
    private val vegetationManager: VegetationManager,
    /**
     * Read lazily: on the live server the value comes from `data/config/npc.yaml`, which is only
     * loaded once `start()` runs.
     */
    gameDayDurationSecondsOf: () -> Double,
) {
    val npcManager: NpcManager =
        NpcManager(
            broadcast = hooks.broadcast,
            getSessions = hooks.getSessions,
            onNpcKilled = hooks.onNpcKilled,
            broadcastCombatLog = hooks.broadcastCombatLog,
            grantNpcKillXp = hooks.grantNpcKillXp,
            ctxOf = hooks.ctxOf,
        )

    val npcSpawner: NpcSpawner = NpcSpawner()

    val gameTimeService: GameTimeService = GameTimeService(gameDayDurationSecondsOf)

    fun build(combatProcessor: CombatProcessor): NpcSubsystem {
        val animals =
            AnimalInteractionProcessor(
                npcManager = npcManager,
                combatProcessor = combatProcessor,
                world = world,
                vegetationManager = vegetationManager,
                gameTimeService = gameTimeService,
                broadcast = hooks.broadcastWorldUpdate,
                ctxOf = hooks.ctxOf,
                canSpawn = hooks.canSpawn,
                onEvent = hooks.onAnimalEvent,
            )

        val packs =
            PackCoordinator(
                    npcManager = npcManager,
                    broadcastCombatLog = hooks.broadcastCombatLog,
                    onEvent = hooks.onPackEvent,
                )
                .also { npcManager.setNpcDamagedByNpcHook(it::onNpcDamagedByNpc) }

        val hibernation =
            HibernationProcessor(
                npcManager = npcManager,
                gameDay = { gameTimeService.currentGameDay },
            )

        return NpcSubsystem(
            npcManager = npcManager,
            npcSpawner = npcSpawner,
            gameTimeService = gameTimeService,
            animals = animals,
            packs = packs,
            hibernation = hibernation,
            pipeline =
                NpcTickPipeline(
                    npcManager = npcManager,
                    npcSpawner = npcSpawner,
                    animals = animals,
                    packs = packs,
                    hibernation = hibernation,
                    ctxOf = hooks.ctxOf,
                    canSpawn = hooks.canSpawn,
                ),
        )
    }

    companion object {
        /**
         * Interval between slow-lane passes (orphan despawn, auto-spawn), in ticks.
         *
         * One definition for both hosts: the live server used to run this from a wall-clock
         * coroutine racing the main tick, while the simulator counted ticks — so the same arena
         * spawned at different rates depending on who was driving it.
         */
        const val LIFECYCLE_INTERVAL_TICKS: Int = 100
    }
}
