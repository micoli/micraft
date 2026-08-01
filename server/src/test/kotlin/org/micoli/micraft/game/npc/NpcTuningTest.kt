package org.micoli.micraft.game.npc

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testWorld

private fun movableDef() =
    NpcDefinition(
        type = "walker",
        behavior = RandomMovableNpcBehavior(),
        behaviorKey = "random_movable",
        bbmodelFile = "walker",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 3f,
        wanderRadius = 10f,
    )

class NpcTuningTest {

    @Test
    fun instanceTuning_doesNotLeakIntoLiveTuning() {
        val liveBefore = NpcConstants.live
        val simTuning = liveBefore.copy(jumpVelocity = 99f, npcZoneSize = 32)
        val ctx = NpcTickContext(simTuning, Random(1))

        assertEquals(99f, ctx.tuning.jumpVelocity)
        assertEquals(liveBefore, NpcConstants.live, "live tunables must be untouched")
        assertNotEquals(simTuning.jumpVelocity, NpcConstants.live.jumpVelocity)
    }

    @Test
    fun wanderStepTicks_comesFromTheInstanceTuning() {
        val tuning = NpcConstants.live.copy(wanderStepTicksMax = 7)
        val instance =
            NpcInstance(
                state =
                    org.micoli.micraft.npc.NpcState(
                        id = "n1",
                        name = "Walker",
                        type = "walker",
                        pos = Vec3(0f, 5f, 0f),
                        yaw = 0f,
                        currentHp = 10,
                        maxHp = 10,
                    ),
                definition = movableDef(),
                spawnPos = Vec3(0f, 5f, 0f),
                tuning = tuning,
            )
        val phase = instance.wanderPhase as WanderPhase.Moving
        assertEquals(7, phase.remainingTicks)
    }

    @Test
    fun npcManager_usesInjectedTuningForUpdateRange() = runBlocking {
        // updateRange 0 means no session is ever close enough to be told about the NPC
        val tuning = NpcConstants.live.copy(updateRange = 0f)
        val manager =
            NpcManager(
                broadcast = {},
                getSessions = { emptyList() },
                ctxOf = { NpcTickContext(tuning, Random(1)) },
            )
        manager.loadDefinitions(mapOf("walker" to movableDef()))
        val instance = manager.spawnNpc("Walker", "walker", Vec3(0f, 5f, 0f))
        assertEquals("walker", instance.state.type)
        assertEquals(1, manager.getAll().size)
    }

    @Test
    fun zoneKey_followsInjectedZoneSize() {
        val manager =
            NpcManager(
                broadcast = {},
                ctxOf = { NpcTickContext(NpcConstants.live.copy(npcZoneSize = 16), Random(1)) },
            )
        assertEquals("2,3", manager.zoneKey(33f, 50f))
    }

    @Test
    fun behavior_readsJumpVelocityFromContext() = runBlocking {
        // wall in front of the NPC at its own level, free one block above → it jumps
        val world = testWorld(Triple(8, 4, 8), Triple(9, 5, 8), Triple(9, 4, 8))
        val instance =
            NpcInstance(
                state =
                    org.micoli.micraft.npc.NpcState(
                        id = "n1",
                        name = "Walker",
                        type = "walker",
                        pos = Vec3(8.5f, 5f, 8.5f),
                        yaw = 0f,
                        currentHp = 10,
                        maxHp = 10,
                    ),
                definition = movableDef(),
                spawnPos = Vec3(8.5f, 5f, 8.5f),
            )
        instance.chaseTargetPos = Vec3(20f, 5f, 8.5f)
        val tuning = NpcConstants.live.copy(jumpVelocity = 3.5f)
        repeat(4) {
            RandomMovableNpcBehavior().tick(instance, world, NpcTickContext(tuning, Random(1)))
        }
        assertTrue(
            instance.vy <= 3.5f,
            "vertical speed must never exceed the injected jump velocity, was ${instance.vy}")
    }
}
