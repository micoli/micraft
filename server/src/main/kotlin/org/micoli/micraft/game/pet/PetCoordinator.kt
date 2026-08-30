package org.micoli.micraft.game.pet

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.Vec3

/**
 * Drives every summoned pet each tick.
 *
 * Out of combat the pet holds a station just to the owner's left and follows it there. In combat it
 * moves to a point right in front of the mob it is fighting — between the mob and the owner — and
 * swings when in range.
 *
 * Runs as an explicit pipeline step *before* `NpcManager.tick`, which leaves a pet's
 * `chaseTargetPos` alone. A pet never sets its own `aggroTarget` / `npcAggroTarget`.
 */
class PetCoordinator(
    private val npcManager: NpcManager,
    private val combatConfig: CombatConfigData,
) {
    suspend fun tick(sessions: Collection<PlayerSession>, combatProcessor: CombatProcessor) {
        val bySessionId = sessions.associateBy { it.id }
        for (pet in npcManager.ownedPets()) {
            val owner = bySessionId[pet.ownerId] ?: continue
            pet.weightless = owner.state.flying
            val target = pickTarget(pet, owner)
            if (target != null) {
                driveCombat(pet, owner, target, combatProcessor)
            } else {
                driveIdle(pet, owner)
            }
            if (pet.weightless) matchAltitude(pet, target?.state?.pos?.y ?: owner.state.pos.y)
        }
    }

    /** Keep a flying pet at [desiredY]; the behaviour's gravity is off, so drive Y here. */
    private suspend fun matchAltitude(pet: NpcInstance, desiredY: Float) {
        val y = pet.state.pos.y
        if (kotlin.math.abs(y - desiredY) < 0.05f) return
        val stepped = y + (desiredY - y).coerceIn(-FLY_STEP, FLY_STEP)
        pet.vy = 0f
        pet.state =
            pet.state.copy(pos = pet.state.pos.copy(y = stepped), vel = pet.state.vel.copy(y = 0f))
        npcManager.refreshNpcState(pet.state.id)
    }

    /** Stand in front of [target] (on the side facing the owner) and attack when in range. */
    private suspend fun driveCombat(
        pet: NpcInstance,
        owner: PlayerSession,
        target: NpcInstance,
        combatProcessor: CombatProcessor,
    ) {
        val tpos = target.state.pos
        val toOwner = normalizeXZ(owner.state.pos.x - tpos.x, owner.state.pos.z - tpos.z)
        val standoff = (combatConfig.npcMaxAttackRange - 0.5f).coerceAtLeast(0.8f)
        pet.chaseTargetPos =
            Vec3(tpos.x + toOwner.first * standoff, tpos.y, tpos.z + toOwner.second * standoff)
        pet.chaseLeash = FOLLOW_LEASH

        val dx = tpos.x - pet.state.pos.x
        val dz = tpos.z - pet.state.pos.z
        val range = combatConfig.npcMaxAttackRange
        if (dx * dx + dz * dz <= range * range) {
            combatProcessor.handleNpcAttackNpc(pet, target)
            target.damageContributors[owner.id] = (target.damageContributors[owner.id] ?: 0) + 1
        }
    }

    /** Hold a station just to the owner's left; teleport in if left far behind. */
    private suspend fun driveIdle(pet: NpcInstance, owner: PlayerSession) {
        val opos = owner.state.pos
        val yaw = owner.state.orientation.yaw
        // forward = (sin yaw, cos yaw); the owner's left is that rotated +90°.
        val leftX = -cos(yaw)
        val leftZ = sin(yaw)
        val station = Vec3(opos.x + leftX * SIDE_OFFSET, opos.y, opos.z + leftZ * SIDE_OFFSET)

        val fromOwner = distXZ(pet.state.pos, opos)
        if (fromOwner > TELEPORT_LEASH) {
            pet.state = pet.state.copy(pos = station, yaw = yaw)
            pet.chaseTargetPos = null
            npcManager.refreshNpcState(pet.state.id)
            return
        }
        // Always hold the station (keeps the behaviour in its chase branch, off the wander/
        // look-around path), and face the same way as the owner once parked on it.
        pet.chaseTargetPos = station
        pet.chaseLeash = FOLLOW_LEASH
        if (distXZ(pet.state.pos, station) <= STATION_DEADZONE && pet.state.yaw != yaw) {
            pet.state = pet.state.copy(yaw = yaw)
            npcManager.refreshNpcState(pet.state.id)
        }
    }

    private fun pickTarget(pet: NpcInstance, owner: PlayerSession): NpcInstance? {
        val combat = owner.combatState
        if (combat.targetIsNpc && combat.targetId != null) {
            val t = npcManager.getInstance(combat.targetId)
            if (t != null && !t.isDead && t.ownerId == null && t.state.id != pet.state.id) return t
        }
        // Nearest wild NPC that is aggroing the owner.
        var best: NpcInstance? = null
        var bestSq = Float.MAX_VALUE
        for (n in npcManager.getAll()) {
            if (n.isDead || n.ownerId != null || n.aggroTarget != owner.id) continue
            val dx = n.state.pos.x - pet.state.pos.x
            val dz = n.state.pos.z - pet.state.pos.z
            val d = dx * dx + dz * dz
            if (d < bestSq) {
                bestSq = d
                best = n
            }
        }
        return best
    }

    private fun distXZ(a: Vec3, b: Vec3): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun normalizeXZ(x: Float, z: Float): Pair<Float, Float> {
        val len = sqrt(x * x + z * z)
        return if (len < 1e-4f) 0f to 1f else x / len to z / len
    }

    companion object {
        private const val SIDE_OFFSET = 1.3f
        private const val STATION_DEADZONE = 0.7f
        // A pet is leashed to its owner (see TELEPORT_LEASH), never to its summon point, so the
        // behaviour's spawn-relative clamp must never fire.
        private const val FOLLOW_LEASH = 1_000_000f
        private const val TELEPORT_LEASH = 40f
        /** Max vertical move per tick while a pet mirrors its flying owner. */
        private const val FLY_STEP = 1.0f
    }
}
