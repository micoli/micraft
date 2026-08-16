package org.micoli.micraft.game.vehicle

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

private val GUARDED_CALLS = listOf("vehicleManager.tick(")

private const val OWNER = "VehicleTickPipeline.kt"

/**
 * Structural guard for the "one owner of the vehicle tick" rule — mirrors `NpcTickOwnershipTest`'s
 * rationale for [VehicleTickPipeline].
 */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class VehicleTickOwnershipTest {

    private fun kotlinSources(): List<Path> =
        mainSources.walk().filter { it.extension == "kt" }.toList()

    @Test
    fun mainSourcesAreReachable() {
        val sources = kotlinSources()
        assertTrue(sources.size > 50, "expected the server sources, found ${sources.size} files")
        assertTrue(sources.any { it.name == OWNER }, "$OWNER must exist")
    }

    @Test
    fun vehicleTickCallsLiveOnlyInThePipeline() {
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
            "Vehicle tick calls must go through $OWNER so the live loop and any future simulator cannot diverge")
    }

    @Test
    fun pipelineActuallyContainsTheGuardedCalls() {
        val pipeline = mainSources.resolve("org/micoli/micraft/game/vehicle/$OWNER").readText()
        GUARDED_CALLS.forEach { call ->
            assertTrue(pipeline.contains(call), "$OWNER should own $call")
        }
    }
}
