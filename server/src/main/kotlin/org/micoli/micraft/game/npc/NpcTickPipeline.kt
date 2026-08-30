package org.micoli.micraft.game.npc

import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.animal.AnimalInteractionProcessor
import org.micoli.micraft.game.npc.pack.PackCoordinator
import org.micoli.micraft.game.pet.PetCoordinator
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState

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
) {
    private var visibilityTickCounter = 0

    private val ctx: NpcTickContext
        get() = ctxOf()

    /** One simulation tick: behaviors, aggro, animal lifecycle, then periodic visibility sync. */
    suspend fun tick(
        world: WorldState,
        sessions: Collection<PlayerSession>,
        combatProcessor: CombatProcessor,
    ) {
        // First: a sleeping NPC must be flagged before the behavior and aggro passes read it.
        hibernation?.tick()
        // Pets pick their target and swing before npcManager.tick reads chaseTargetPos.
        pets?.tick(sessions, combatProcessor)
        npcManager.tick(world)
        // Before tickAggro so a target picked this tick is acted on in the same tick.
        packs?.tick()
        npcManager.tickAggro(sessions, combatProcessor)
        animals.tick()
        visibilityTickCounter++
        if (visibilityTickCounter >= ctx.tuning.npcVisibilityCheckIntervalTicks) {
            visibilityTickCounter = 0
            npcManager.tickVisibility(sessions)
        }
    }

    /**
     * A player left: NPCs that nobody is near are parked for respawn. Same operation the slow lane
     * performs, exposed separately so disconnect handling does not reach into the manager directly.
     */
    suspend fun onPlayerDisconnected(sessions: Collection<PlayerSession>) {
        npcManager.despawnOrphanedNpcs(sessions)
    }

    /** Slow lane (every few seconds): drop NPCs nobody can see, then auto-spawn near players. */
    suspend fun lifecycle(world: WorldState, sessions: Collection<PlayerSession>) {
        npcManager.despawnOrphanedNpcs(sessions)
        npcSpawner.trySpawn(
            world,
            npcManager,
            npcManager.getDefinitions(),
            nearChunks(world, sessions),
            ctx,
            canSpawn)
    }

    /** Zone cell a world position falls into. */
    fun zoneOf(x: Float, z: Float): Pair<Int, Int> {
        val size = ctx.tuning.npcZoneSize
        return Pair(Math.floorDiv(x.toInt(), size), Math.floorDiv(z.toInt(), size))
    }

    /**
     * A player entered zone ([zoneX], [zoneZ]): bring back what was orphan-despawned there and give
     * the spawner a chance on the 3×3 neighbourhood.
     */
    suspend fun onZoneCrossed(world: WorldState, zoneX: Int, zoneZ: Int) {
        val size = ctx.tuning.npcZoneSize
        val adjacentChunks = mutableListOf<ChunkPos>()
        for (dzx in -1..1) for (dzz in -1..1) {
            val zx = zoneX + dzx
            val zz = zoneZ + dzz
            npcManager.respawnPendingInZone(zx, zz)
            world.discoveredChunks().filterTo(adjacentChunks) { cp ->
                Math.floorDiv(cp.cx * WorldConstants.CHUNK_SIZE, size) == zx &&
                    Math.floorDiv(cp.cz * WorldConstants.CHUNK_SIZE, size) == zz
            }
        }
        if (adjacentChunks.isNotEmpty()) {
            npcSpawner.trySpawn(
                world, npcManager, npcManager.getDefinitions(), adjacentChunks, ctx, canSpawn)
        }
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
}
