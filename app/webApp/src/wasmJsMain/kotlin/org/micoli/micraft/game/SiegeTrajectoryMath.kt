package org.micoli.micraft.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.micoli.micraft.player.Vec3

/**
 * Client-side mirror of `SiegeWeaponManager.computeMuzzleAndVelocity` (server,
 * `server/.../game/placeable/siege/SiegeWeaponManager.kt`) — used only to render Phase D's
 * trajectory preview before firing. Kept as a duplicated pure function rather than a shared `core`
 * one since the server function lives in the server module (not `core`), which the wasmJs client
 * target cannot depend on; **any change to the server formula must be mirrored here** or the
 * preview will visually diverge from the real shot.
 */
object SiegeTrajectoryMath {
    private val DEG_TO_RAD = (PI / 180.0).toFloat()
    private val YAW_STEP_RAD = (PI / 6.0).toFloat() // 12 steps -> 30° increments

    /**
     * @param placeablePos position of the linked placeable
     * @param rotationStep 0..11, 30° increments (same convention as
     *   [org.micoli.micraft.placeable.PlaceableState])
     * @param muzzleOffset weapon-local muzzle offset, rotated by [rotationStep]'s yaw before being
     *   added to [placeablePos]
     * @param launchPitchDeg base pitch (degrees), [pitchStep] nudges it by one unit each
     * @param pitchStep current pitch adjustment step
     * @param launchPower base power, [powerStep] nudges it by one unit each
     * @param powerStep current power adjustment step
     */
    fun computeMuzzleAndVelocity(
        placeablePos: Vec3,
        rotationStep: Int,
        muzzleOffset: Vec3,
        launchPitchDeg: Float,
        pitchStep: Int,
        launchPower: Float,
        powerStep: Int,
    ): Pair<Vec3, Vec3> {
        val yaw = rotationStep * YAW_STEP_RAD
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)

        val muzzle =
            Vec3(
                x = placeablePos.x + (muzzleOffset.x * cosYaw - muzzleOffset.z * sinYaw),
                y = placeablePos.y + muzzleOffset.y,
                z = placeablePos.z + (muzzleOffset.x * sinYaw + muzzleOffset.z * cosYaw))

        val pitchRad = (launchPitchDeg + pitchStep) * DEG_TO_RAD
        val power = launchPower + powerStep
        val horizontal = power * cos(pitchRad)
        val velocity =
            Vec3(x = horizontal * -sinYaw, y = power * sin(pitchRad), z = horizontal * cosYaw)

        return muzzle to velocity
    }
}
