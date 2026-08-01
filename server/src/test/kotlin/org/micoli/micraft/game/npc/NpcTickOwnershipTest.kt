package org.micoli.micraft.game.npc

import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val projectRoot: Path = Path.of(System.getProperty("projectDir", ".."))

private val mainSources: Path = projectRoot.resolve("server/src/main/kotlin")

/** Call sites that must exist in exactly one place: the pipeline. */
private val GUARDED_CALLS =
    listOf(
        "npcManager.tick(",
        "npcManager.tickAggro(",
        "npcManager.tickVisibility(",
        "npcManager.despawnOrphanedNpcs(",
        "npcManager.respawnPendingInZone(",
        "npcSpawner.trySpawn(",
        "animals.tick(",
        "animalInteractionProcessor.tick(",
    )

private const val OWNER = "NpcTickPipeline.kt"

/**
 * Structural guard for the "one owner of the NPC tick" rule (see [NpcTickPipeline]).
 *
 * If the live game loop and the admin world simulator each drive NPCs their own way, they drift and
 * the simulator silently lies about the real rules. This test fails as soon as any of the guarded
 * calls reappears outside the pipeline — the fix is to route the new caller through
 * [NpcTickPipeline], not to relax this list.
 */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class NpcTickOwnershipTest {

    private fun kotlinSources(): List<Path> =
        mainSources.walk().filter { it.extension == "kt" }.toList()

    @Test
    fun mainSourcesAreReachable() {
        val sources = kotlinSources()
        assertTrue(sources.size > 50, "expected the server sources, found ${sources.size} files")
        assertTrue(sources.any { it.name == OWNER }, "$OWNER must exist")
    }

    @Test
    fun npcTickCallsLiveOnlyInThePipeline() {
        val offenders = mutableListOf<String>()
        for (file in kotlinSources()) {
            if (file.name == OWNER) continue
            val text = file.readText()
            for (call in GUARDED_CALLS) {
                if (text.contains(call)) {
                    offenders += "${file.name} calls $call"
                }
            }
        }
        assertEquals(
            emptyList(),
            offenders,
            "NPC tick calls must go through $OWNER so the live loop and the world simulator cannot diverge")
    }

    @Test
    fun pipelineActuallyContainsTheGuardedCalls() {
        val pipeline = mainSources.resolve("org/micoli/micraft/game/npc/$OWNER").readText()
        listOf(
                "npcManager.tick(",
                "npcManager.tickAggro(",
                "npcManager.tickVisibility(",
                "animals.tick(",
                "npcSpawner.trySpawn(",
            )
            .forEach { call -> assertTrue(pipeline.contains(call), "$OWNER should own $call") }
    }
}
