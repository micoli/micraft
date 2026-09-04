package org.micoli.micraft.game.placeable.siege

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.PlayerConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.placeable.siege.SiegeProjectileRegistry
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SiegeProjectileManager::class.java)

/**
 * Fallback collision-sphere radius when a projectile type has no [SiegeProjectileRegistry] entry.
 */
private const val DEFAULT_PROJECTILE_RADIUS = 0.3f

/**
 * Spawn/tick/impact bookkeeping for flying siege projectiles — mirrors the spawn half of
 * [org.micoli.micraft.game.vehicle.VehicleManager], plus a physics tick loop
 * ([org.micoli.micraft.game.vehicle.VehicleBehavior]-style) and impact damage. Transient only: no
 * load/save, a projectile never survives a server restart mid-flight.
 */
class SiegeProjectileManager(private val broadcast: suspend (ServerMessage) -> Unit) {
    private val projectiles = ConcurrentHashMap<String, SiegeProjectileInstance>()

    fun getAll(): Collection<SiegeProjectileInstance> = projectiles.values

    /** Spawns a new projectile and broadcasts it. Doesn't validate [type] against the registry. */
    suspend fun spawnProjectile(
        type: EntityType,
        pos: Vec3,
        velocity: Vec3,
        ownerId: String,
        impactRadius: Float,
        impactDamage: Int,
        damageType: DamageType = DamageType.PHYSICAL,
    ): SiegeProjectileInstance {
        val instance =
            SiegeProjectileInstance(
                id = UUID.randomUUID().toString(),
                type = type,
                pos = pos,
                velocity = velocity,
                ownerId = ownerId,
                impactRadius = impactRadius,
                impactDamage = impactDamage,
                damageType = damageType,
            )
        projectiles[instance.id] = instance
        broadcast(ServerMessage.SiegeProjectileSpawned(instance.toState()))
        log.debug("Spawned projectile {} ({}) at {} velocity={}", instance.id, type, pos, velocity)
        return instance
    }

    /** Replays every currently-flying projectile to a newly-connected session. */
    suspend fun sendAllTo(session: PlayerSession) {
        for (instance in projectiles.values) session.send(
            ServerMessage.SiegeProjectileSpawned(instance.toState()))
    }

    /**
     * One simulation tick for every flying projectile: advances physics, detects terrain/entity
     * impact, and on impact removes the projectile, broadcasts
     * [ServerMessage.SiegeProjectileImpact], and runs the direct-damage AoE loop (shape copied from
     * `SpellProcessor`'s NECROTIC_AOE branch, calling real damage instead of a status effect).
     */
    suspend fun tick(
        world: WorldState,
        sessions: Collection<PlayerSession>,
        npcManager: NpcManager,
        combatProcessor: CombatProcessor,
    ) {
        if (projectiles.isEmpty()) return
        val toRemove = mutableListOf<String>()
        for (instance in projectiles.values) {
            val prevPos = instance.pos
            val terrainImpact = SiegeProjectileBehavior.tick(instance, world, TICK_SECONDS)
            val projectileRadius =
                SiegeProjectileRegistry.get(instance.type)?.radius ?: DEFAULT_PROJECTILE_RADIUS
            val entityHit =
                findEntityHit(prevPos, instance.pos, projectileRadius, sessions, npcManager)

            if (terrainImpact == null && !entityHit) {
                broadcast(ServerMessage.SiegeProjectileUpdate(instance.toState()))
                continue
            }

            toRemove += instance.id
            val impactPos = instance.pos
            broadcast(
                ServerMessage.SiegeProjectileImpact(
                    impactPos.x, impactPos.y, impactPos.z, instance.impactRadius))
            applyImpactDamage(instance, impactPos, sessions, npcManager, combatProcessor)
        }
        toRemove.forEach { projectiles.remove(it) }
    }

    /**
     * True if any live session or NPC's position lies within [projectileRadius] plus its own body
     * radius of the segment [from]→[to] this tick traveled — a simple point-to-segment sphere test,
     * not sub-tick-precise (acceptable: the terrain sub-stepping already prevents tunneling, and
     * this only needs to catch "was something roughly on this tick's path").
     */
    private fun findEntityHit(
        from: Vec3,
        to: Vec3,
        projectileRadius: Float,
        sessions: Collection<PlayerSession>,
        npcManager: NpcManager,
    ): Boolean {
        val playerRadius = PlayerConstants.WIDTH / 2f
        for (session in sessions) {
            val combined = projectileRadius + playerRadius
            if (distancePointToSegment(session.state.pos, from, to) <= combined) return true
        }
        for (npc in npcManager.getAll()) {
            if (npc.isDead) continue
            val combined = projectileRadius + npc.definition.width / 2f
            if (distancePointToSegment(npc.state.pos, from, to) <= combined) return true
        }
        return false
    }

    private suspend fun applyImpactDamage(
        instance: SiegeProjectileInstance,
        impactPos: Vec3,
        sessions: Collection<PlayerSession>,
        npcManager: NpcManager,
        combatProcessor: CombatProcessor,
    ) {
        val radiusSq = instance.impactRadius * instance.impactRadius
        val now = System.currentTimeMillis()
        val isFlaming = instance.damageType == DamageType.FIRE

        for (target in sessions) {
            if (target.characterData == null) continue
            val tp = target.state.pos
            val ex = impactPos.x - tp.x
            val ey = impactPos.y - tp.y
            val ez = impactPos.z - tp.z
            if (ex * ex + ey * ey + ez * ez > radiusSq) continue
            combatProcessor.applyDirectDamage(target, instance.impactDamage, "Siege weapon")
            if (isFlaming)
                combatProcessor.applyStatusEffectTo(
                    target, StatusEffect.Burning, StatusEffect.Burning.durationSec, now)
        }

        for (npc in npcManager.getAll()) {
            if (npc.isDead) continue
            val np = npc.state.pos
            val ex = impactPos.x - np.x
            val ey = impactPos.y - np.y
            val ez = impactPos.z - np.z
            if (ex * ex + ey * ey + ez * ez > radiusSq) continue
            npcManager.applyDamage(npc.state.id, instance.impactDamage, instance.ownerId)
            if (isFlaming)
                npcManager.applyStatusEffectDirectly(
                    npc.state.id, StatusEffect.Burning, StatusEffect.Burning.durationSec, now)
        }
    }

    companion object {
        /** Shortest distance from [p] to the segment [a]→[b] — used for the entity hit test. */
        fun distancePointToSegment(p: Vec3, a: Vec3, b: Vec3): Float {
            val abx = b.x - a.x
            val aby = b.y - a.y
            val abz = b.z - a.z
            val lenSq = abx * abx + aby * aby + abz * abz
            val t =
                if (lenSq < 1e-6f) 0f
                else
                    (((p.x - a.x) * abx + (p.y - a.y) * aby + (p.z - a.z) * abz) / lenSq).coerceIn(
                        0f, 1f)
            val cx = a.x + abx * t
            val cy = a.y + aby * t
            val cz = a.z + abz * t
            val dx = p.x - cx
            val dy = p.y - cy
            val dz = p.z - cz
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
    }
}
