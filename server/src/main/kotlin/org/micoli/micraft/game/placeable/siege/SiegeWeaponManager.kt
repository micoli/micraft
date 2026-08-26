package org.micoli.micraft.game.placeable.siege

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.game.placeable.PlaceableInstance
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.placeable.siege.SiegeWeaponDefinition
import org.micoli.micraft.placeable.siege.SiegeWeaponRegistry
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SiegeWeaponManager")

/**
 * Siege-specific sub-system layered on top of [org.micoli.micraft.game.placeable.PlaceableManager]
 * — never reimplements position/orientation, only the extra fields (pitch, power, cooldown) a siege
 * weapon carries. Each instance references its
 * [org.micoli.micraft.game.placeable.PlaceableInstance] by id (composition, not inheritance).
 */
class SiegeWeaponManager(private val broadcast: suspend (ServerMessage) -> Unit) {
    private val weapons = ConcurrentHashMap<String, SiegeWeaponInstance>()

    fun getAll(): Collection<SiegeWeaponInstance> = weapons.values

    fun get(id: String): SiegeWeaponInstance? = weapons[id]

    fun getByPlaceableId(placeableId: String): SiegeWeaponInstance? =
        weapons.values.firstOrNull { it.placeableId == placeableId }

    /**
     * Links a new siege instance to [placeable] without broadcasting — a no-op if [placeable]'s
     * type isn't registered in [SiegeWeaponRegistry]. Used both by [spawnFor] and by boot-time
     * restore (see `GameLoop.start`), which re-links from the persisted placeable list before any
     * session is connected to broadcast to.
     */
    fun linkFor(placeable: PlaceableInstance): SiegeWeaponInstance? {
        if (SiegeWeaponRegistry.get(placeable.type) == null) return null
        val instance =
            SiegeWeaponInstance(UUID.randomUUID().toString(), placeable.id, placeable.type)
        weapons[instance.id] = instance
        log.debug("Linked siege weapon {} to placeable {}", instance.id, placeable.id)
        return instance
    }

    /**
     * Links a new siege instance to [placeable] and broadcasts it — a no-op if [placeable]'s type
     * isn't registered in [SiegeWeaponRegistry] (called unconditionally right after a placeable
     * spawn; the caller doesn't need to check the registry itself).
     */
    suspend fun spawnFor(placeable: PlaceableInstance): SiegeWeaponInstance? {
        val instance = linkFor(placeable) ?: return null
        broadcast(ServerMessage.SiegeWeaponUpdate(instance.toState()))
        return instance
    }

    /**
     * Drops whichever siege instance is linked to [placeableId], if any — call on placeable
     * despawn.
     */
    fun despawnFor(placeableId: String) {
        weapons.values.removeIf { it.placeableId == placeableId }
    }

    /** Sets the absolute pitch step, clamped to the linked definition's `pitchStepRange`. */
    suspend fun handleSetPitch(id: String, value: Int) {
        val instance = weapons[id] ?: return
        val range = SiegeWeaponRegistry.get(instance.type)?.pitchStepRange ?: DEFAULT_STEP_RANGE
        instance.pitchStep = value.coerceIn(0, range)
        broadcast(ServerMessage.SiegeWeaponUpdate(instance.toState()))
    }

    /**
     * Advances the pitch step by one in [SiegeWeaponInstance.pitchDirection] — the R-key relative
     * nudge (as opposed to [handleSetPitch]'s absolute set). Flips direction first whenever the
     * next step would push `launchPitchDeg + pitchStep` past
     * `launchPitchDegMin`/`launchPitchDegMax`, so repeated presses bounce the pitch back and forth
     * between the two bounds instead of sticking. Notifies [session] with the resulting current
     * pitch.
     */
    suspend fun handleNudgePitch(session: PlayerSession, id: String) {
        val instance = weapons[id] ?: return
        val def = SiegeWeaponRegistry.get(instance.type) ?: return
        val nextAngle = def.launchPitchDeg + instance.pitchStep + instance.pitchDirection
        if (nextAngle > def.launchPitchDegMax) instance.pitchDirection = -1
        else if (nextAngle < def.launchPitchDegMin) instance.pitchDirection = 1
        instance.pitchStep += instance.pitchDirection
        broadcast(ServerMessage.SiegeWeaponUpdate(instance.toState()))
        session.send(
            ServerMessage.Notification("Pitch: ${def.launchPitchDeg + instance.pitchStep}"))
    }

    /** Sets the absolute power step, clamped to the linked definition's `powerStepRange`. */
    suspend fun handleSetPower(id: String, value: Int) {
        val instance = weapons[id] ?: return
        val range = SiegeWeaponRegistry.get(instance.type)?.powerStepRange ?: DEFAULT_STEP_RANGE
        instance.powerStep = value.coerceIn(0, range)
        broadcast(ServerMessage.SiegeWeaponUpdate(instance.toState()))
    }

    /**
     * Advances the power step by one in [SiegeWeaponInstance.powerDirection] — the R-key relative
     * nudge (as opposed to [handleSetPower]'s absolute set). Flips direction first whenever the
     * next step would push `launchPower + powerStep` past `launchPowerMin`/`launchPowerMax`, so
     * repeated presses bounce the power back and forth between the two bounds instead of sticking.
     * Notifies [session] with the resulting current power.
     */
    suspend fun handleNudgePower(session: PlayerSession, id: String) {
        val instance = weapons[id] ?: return
        val def = SiegeWeaponRegistry.get(instance.type) ?: return
        val nextPower = def.launchPower + instance.powerStep + instance.powerDirection
        if (nextPower > def.launchPowerMax) instance.powerDirection = -1
        else if (nextPower < def.launchPowerMin) instance.powerDirection = 1
        instance.powerStep += instance.powerDirection
        broadcast(ServerMessage.SiegeWeaponUpdate(instance.toState()))
        session.send(ServerMessage.Notification("Power: ${def.launchPower + instance.powerStep}"))
    }

    /** Replays every currently-spawned siege weapon to a newly-connected session. */
    suspend fun sendAllTo(session: PlayerSession) {
        for (instance in weapons.values) session.send(
            ServerMessage.SiegeWeaponUpdate(instance.toState()))
    }

    /**
     * Fires the siege weapon linked to placeable [placeableId] on behalf of [session] — gates on
     * cooldown and ammo, then consumes ammo and resets the cooldown on success. [world] isn't used
     * yet (Phase B does no ground/trajectory validation); it's threaded through so Phase C's
     * projectile spawn can be wired in without changing this call site.
     *
     * Returns the computed muzzle position + launch velocity on success, or null if rejected
     * (weapon/placeable not found, on cooldown, or no ammo) — nothing is mutated or broadcast on
     * rejection.
     *
     * On success, spawns a real physics projectile via [siegeProjectileManager] (Phase C) using
     * this same muzzle/velocity computation, tagged [DamageType.FIRE] when the weapon's configured
     * `ammoItem` is `FLAMING_BOULDER` — still also broadcasts the Phase B
     * [ServerMessage.SiegeWeaponFired] placeholder as a lightweight "shot fired" notice alongside
     * the real spawn.
     */
    suspend fun fire(
        session: PlayerSession,
        placeableId: String,
        placeableManager: PlaceableManager,
        world: WorldState,
        siegeProjectileManager: SiegeProjectileManager,
    ): Pair<Vec3, Vec3>? {
        val weapon = getByPlaceableId(placeableId) ?: return null
        val placeable = placeableManager.get(placeableId) ?: return null
        val def = SiegeWeaponRegistry.get(weapon.type) ?: return null

        val now = System.currentTimeMillis()
        if (now < weapon.cooldownUntilMs) {
            session.send(ServerMessage.Notification("Siege weapon on cooldown"))
            return null
        }

        val ammoItem = def.ammoItem
        val ammoCount = ammoItem?.let { session.inventory[it] } ?: 0
        if (ammoItem == null || ammoCount <= 0) {
            session.send(ServerMessage.Notification("No ammunition for this siege weapon"))
            return null
        }

        val remaining = ammoCount - 1
        if (remaining <= 0) session.inventory.remove(ammoItem)
        else session.inventory[ammoItem] = remaining
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))

        weapon.cooldownUntilMs = now + def.cooldownMs
        broadcast(ServerMessage.SiegeWeaponUpdate(weapon.toState()))

        val (muzzle, velocity) = computeMuzzleAndVelocity(placeable, weapon, def)
        broadcast(ServerMessage.SiegeWeaponFired(weapon.id, muzzle, velocity))

        val isFlaming = ammoItem == FLAMING_BOULDER_ITEM
        siegeProjectileManager.spawnProjectile(
            type = EntityType(def.projectileType),
            pos = muzzle,
            velocity = velocity,
            ownerId = session.id,
            impactRadius = def.impactRadius,
            impactDamage = def.impactDamage,
            damageType = if (isFlaming) DamageType.FIRE else DamageType.PHYSICAL,
        )
        log.debug("Fired siege weapon {} muzzle={} velocity={}", weapon.id, muzzle, velocity)
        return muzzle to velocity
    }

    companion object {
        private const val DEFAULT_STEP_RANGE = 10
        private val DEG_TO_RAD = (PI / 180.0).toFloat()
        private val YAW_STEP_RAD = (PI / 6.0).toFloat() // 12 steps -> 30° increments

        /**
         * A weapon's `ammoItem` is a single fixed [ItemType] (no per-shot ammo choice yet — see
         * `SiegeWeaponYamlEntry`), so whether a shot is "flaming" is decided entirely by the
         * weapon's configured ammo, not by inventory contents: CATAPULT always fires plain
         * `BOULDER`, TREBUCHET always fires `FLAMING_BOULDER`.
         */
        private val FLAMING_BOULDER_ITEM = ItemType("FLAMING_BOULDER")

        /**
         * Pure function: derives world-space muzzle position and initial launch velocity from
         * [placeable]'s position/orientation composed with [siege]'s pitch/power steps and [def]'s
         * base stats. Kept separate from [fire] so Phase C's projectile spawn can call it directly
         * without duplicating the math.
         *
         * Orientation convention: `rotationStep * 30°` is the yaw, matching the client's
         * `model.root.rotation.y` (see `placeableModel.ts` `setPlaceableTransform`). Forward at
         * yaw=0 is +Z; [SiegeWeaponDefinition.muzzleOffset]'s XZ is rotated by the same yaw before
         * being added to the placeable's position.
         */
        fun computeMuzzleAndVelocity(
            placeable: PlaceableInstance,
            siege: SiegeWeaponInstance,
            def: SiegeWeaponDefinition,
        ): Pair<Vec3, Vec3> {
            val yaw = placeable.rotationStep * YAW_STEP_RAD
            val cosYaw = cos(yaw)
            val sinYaw = sin(yaw)

            val offset = def.muzzleOffset
            val muzzle =
                Vec3(
                    x = placeable.pos.x + (offset.x * cosYaw - offset.z * sinYaw),
                    y = placeable.pos.y + offset.y,
                    z = placeable.pos.z + (offset.x * sinYaw + offset.z * cosYaw))

            // Each pitch/power step nudges the base stat by one unit — Phase C/D own the real
            // per-step scale once trajectory feel can be tuned against actual physics.
            val pitchRad = (def.launchPitchDeg + siege.pitchStep) * DEG_TO_RAD
            val power = def.launchPower + siege.powerStep
            val horizontal = power * cos(pitchRad)
            val velocity =
                Vec3(x = horizontal * -sinYaw, y = power * sin(pitchRad), z = horizontal * cosYaw)

            return muzzle to velocity
        }
    }
}
