package org.micoli.micraft.game.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.npc.NpcSubsystemFactory
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.SeededRegionsGenerator
import org.micoli.micraft.support.testSession

private val shared by lazy { SharedGameServices.default() }

class GameWorldRegionChangeThrottleTest {

    @Test
    fun oscillatingAcrossARegionBorder_triggersOnRegionEnteredAtMostOncePerCooldownWindow() =
        runBlocking {
            // Two Regions whose border is x = 0.
            val world =
                buildGameWorld(
                    "region-throttle-test",
                    SeededRegionsGenerator(listOf(-10 to 8, 10 to 8)),
                    shared)
            val regionA = Vec3(-6f, 9f, 8f)
            val regionB = Vec3(6f, 9f, 8f)
            val session = testSession(pos = regionA)
            world.onPlayerJoin(session)

            var changes = 0
            var lastSeen: Long? = session.lastRegionChangeTick
            repeat(NpcSubsystemFactory.REGION_CHANGE_COOLDOWN_TICKS * 2) { i ->
                session.state = session.state.copy(pos = if (i % 2 == 0) regionA else regionB)
                world.tick()
                if (session.lastRegionChangeTick != lastSeen) {
                    changes++
                    lastSeen = session.lastRegionChangeTick
                }
            }

            // One pass on the initial join (tick 0), one more once the cooldown window elapses —
            // never one per oscillation.
            assertEquals(
                2,
                changes,
                "crossing a Region border every tick must not re-run the spawn scan every tick")
        }
}
