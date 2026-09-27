package org.micoli.micraft.game.npc

import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.combat.SpellProcessor
import org.micoli.micraft.game.npc.animal.AnimalInteractionProcessor
import org.micoli.micraft.game.npc.pack.PackCoordinator
import org.micoli.micraft.game.npc.roster.RegionPopulation
import org.micoli.micraft.game.pet.PetCoordinator
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.player.Vec3

/**
 * Single owner of the NPC tick sequence and its cadences.
 *
 * Both the live [org.micoli.micraft.game.GameLoop] and the admin world simulator drive NPCs through
 * this class, so a rule change is observed identically in both. Do not call `npcManager.tick`,
 * `tickAggro`, `tickVisibility`, `animals.tick`, `pets.tick` or `npcSpawner.trySpawn` from anywhere
 * else — `NpcTickOwnershipTest` fails if you do.
 */
class NpcTickPipeline(
    private val npcManager: NpcManager,
    private val npcSpawner: NpcSpawner,
    private val animals: AnimalInteractionProcessor,
    private val packs: PackCoordinator? = null,
    private val hibernation: HibernationProcessor? = null,
    private val pets: PetCoordinator? = null,
    private val ctxOf: () -> NpcTickContext = { NpcTickContext.live },
    /** Veto on auto-spawning; the admin simulator refuses past its population ceiling. */
    private val canSpawn: () -> Boolean = { true },
    private val questGiverSpawner: QuestGiverSpawner? = null,
) {
    private val population: RegionPopulation
        get() = npcSpawner.population

    private var visibilityTickCounter = 0

    private val ctx: NpcTickContext
        get() = ctxOf()

    /** One simulation tick: behaviors, aggro, animal lifecycle, then periodic visibility sync. */
    suspend fun tick(
        world: WorldState,
        sessions: Collection<PlayerSession>,
        combatProcessor: CombatProcessor,
        spellProcessor: SpellProcessor? = null,
    ) {
        // First: a sleeping NPC must be flagged before the behavior and aggro passes read it.
        hibernation?.tick()
        // Pets pick their target and swing before npcManager.tick reads chaseTargetPos.
        pets?.tick(sessions, combatProcessor)
        npcManager.tick(world)
        // Before tickAggro so a target picked this tick is acted on in the same tick.
        packs?.tick()
        npcManager.tickAggro(sessions, combatProcessor, spellProcessor)
        animals.tick()
        visibilityTickCounter++
        if (visibilityTickCounter >= ctx.tuning.npcVisibilityCheckIntervalTicks) {
            visibilityTickCounter = 0
            npcManager.tickVisibility(sessions)
        }
    }

    /** A player left: Regions nobody is in any more are parked. */
    suspend fun onPlayerDisconnected(world: WorldState, sessions: Collection<PlayerSession>) {
        parkInactiveRegions(world, activeRegions(world, sessions))
    }

    /** Slow lane (every few seconds): park inactive Regions, then fill the active ones. */
    suspend fun lifecycle(world: WorldState, sessions: Collection<PlayerSession>) {
        val active = activeRegions(world, sessions)
        parkInactiveRegions(world, active)
        spawnIn(world, active, nearChunks(world, sessions))
    }

    /**
     * [session] walked into a new Region: bring back what was parked around it and give the
     * spawners a pass there.
     */
    suspend fun onRegionEntered(world: WorldState, session: PlayerSession) {
        val active = activeRegions(world, listOf(session))
        active.forEach { npcManager.respawnParked(it) }
        spawnIn(world, active, nearChunks(world, listOf(session)))
    }

    private suspend fun spawnIn(world: WorldState, active: Set<Long>, chunks: List<ChunkPos>) {
        val inActiveRegions =
            chunks.filter { chunk -> world.regionAt(centerOf(chunk))?.key in active }
        if (inActiveRegions.isEmpty()) return
        npcSpawner.trySpawn(
            world, npcManager, npcManager.getDefinitions(), inActiveRegions, ctx, canSpawn)
        questGiverSpawner?.trySpawn(world, npcManager, npcManager.getDefinitions(), inActiveRegions)
    }

    /** The Region of each Character and the Regions around it (ADR-0010). */
    private fun activeRegions(world: WorldState, sessions: Collection<PlayerSession>): Set<Long> =
        sessions
            .mapNotNull { world.regionAt(it.state.pos) }
            .flatMap { region ->
                world.regionsNear(
                    region.seedX, region.seedZ, NEIGHBOUR_RADIUS_ZONES * ctx.tuning.npcZoneSize) +
                    region
            }
            .mapTo(HashSet()) { it.key }

    /**
     * Takes NPCs of inactive Regions out of the World. Quest givers are dropped (their spawner
     * places them again with fresh offers) and so are wild NPCs no longer in their Region's Roster;
     * Pets follow their owner and are never parked.
     */
    private suspend fun parkInactiveRegions(world: WorldState, active: Set<Long>) {
        val outside =
            npcManager
                .getAll()
                .filter { it.ownerId == null }
                .mapNotNull { npc -> world.regionAt(npc.state.pos)?.let { npc to it } }
                .filter { (_, region) -> region.key !in active }
        for ((npc, region) in outside) {
            val keep = !npc.definition.isQuestGiver && population.fitsRoster(npc, region)
            if (keep) npcManager.park(npc, region.key) else npcManager.despawnNpc(npc.state.id)
        }
    }

    private fun centerOf(chunk: ChunkPos): Vec3 {
        val half = WorldConstants.CHUNK_SIZE / 2f
        return Vec3(
            chunk.cx * WorldConstants.CHUNK_SIZE + half,
            0f,
            chunk.cz * WorldConstants.CHUNK_SIZE + half)
    }

    /**
     * Chunks worth considering for spawning: within [ctx.tuning.npcZoneSize] of some player.
     *
     * Walks a fixed box per session and keeps only chunks actually discovered, rather than
     * filtering [WorldState.discoveredChunks] — that set only grows over a server's lifetime, so
     * scanning all of it here got slower the longer the world had been explored, independent of how
     * many chunks were actually near a player right now.
     */
    private fun nearChunks(world: WorldState, sessions: Collection<PlayerSession>): List<ChunkPos> {
        if (sessions.isEmpty()) return emptyList()
        val halfZone = ctx.tuning.npcZoneSize / WorldConstants.CHUNK_SIZE
        val result = LinkedHashSet<ChunkPos>()
        for (s in sessions) {
            val pcx = Math.floorDiv(s.state.pos.x.toInt(), WorldConstants.CHUNK_SIZE)
            val pcz = Math.floorDiv(s.state.pos.z.toInt(), WorldConstants.CHUNK_SIZE)
            for (dx in -halfZone..halfZone) {
                for (dz in -halfZone..halfZone) {
                    val cp = ChunkPos(pcx + dx, pcz + dz)
                    if (world.getChunkIfDiscovered(cp) != null) result.add(cp)
                }
            }
        }
        return result.toList()
    }

    private companion object {
        /** Neighbouring Voronoi seeds lie within about two cells of a Region's own seed. */
        const val NEIGHBOUR_RADIUS_ZONES = 2
    }
}
