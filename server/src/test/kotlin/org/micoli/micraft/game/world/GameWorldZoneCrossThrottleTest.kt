package org.micoli.micraft.game.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.npc.NpcSubsystemFactory
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.EndToEndBoundedChunkGenerator
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testSession

private val shared by lazy { SharedGameServices.default() }

private fun gen() = EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1)

class GameWorldZoneCrossThrottleTest {

    @Test
    fun oscillatingAcrossAZoneBoundary_triggersOnZoneCrossedAtMostOncePerCooldownWindow() =
        runBlocking {
            val world = buildGameWorld("zone-throttle-test", gen(), shared)
            // npcZoneSize defaults to 256: x=-6 (zone -1) and x=6 (zone 0) straddle the x=0
            // boundary, well within EndToEndBoundedChunkGenerator's default +/-16 block bounds.
            val zoneA = Vec3(-6f, 65f, 8f)
            val zoneB = Vec3(6f, 65f, 8f)
            val session = testSession(pos = zoneA)
            world.onPlayerJoin(session)

            var crossings = 0
            var lastSeen: Long? = session.lastZoneCrossTick
            repeat(NpcSubsystemFactory.ZONE_CROSS_COOLDOWN_TICKS * 2) { i ->
                session.state = session.state.copy(pos = if (i % 2 == 0) zoneA else zoneB)
                world.tick()
                if (session.lastZoneCrossTick != lastSeen) {
                    crossings++
                    lastSeen = session.lastZoneCrossTick
                }
            }

            // One dispatch on the initial join (tick 0), one more once the cooldown window
            // elapses (tick ZONE_CROSS_COOLDOWN_TICKS) — never one per oscillation.
            assertEquals(
                2,
                crossings,
                "oscillating every tick across a zone boundary must only re-trigger the spawn " +
                    "scan once per cooldown window, not on every tick")
        }
}
