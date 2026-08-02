package org.micoli.micraft.game.npc.pack

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.player.Vec3

private const val GROUND = 8f

private fun packConfig(
    extend: List<String> = emptyList(),
    callRadius: Float = 10f,
    relayHops: Int = 3,
    maxSize: Int = 6,
    minSizeToEngage: Int = 3,
    callCooldownSec: Float = 20f,
    rallyTimeoutSec: Float = 20f,
) =
    PackConfig(
        extendPackType = extend,
        callRadius = callRadius,
        relayHops = relayHops,
        maxSize = maxSize,
        minSizeToEngage = minSizeToEngage,
        callCooldownSec = callCooldownSec,
        rallyTimeoutSec = rallyTimeoutSec,
        chaseRadius = 45f,
        hostileTypes = listOf("bear"),
    )

private fun def(type: String, pack: PackConfig? = null, aggroRange: Float = 6f) =
    NpcDefinition(
        type = type,
        behavior = RandomMovableNpcBehavior(),
        behaviorKey = "random_movable",
        bbmodelFile = type,
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 2f,
        wanderRadius = 20f,
        hp = 20,
        aggroRange = aggroRange,
        packConfig = pack,
    )

private class Harness(
    wolfPack: PackConfig = packConfig(),
    veteranPack: PackConfig? = packConfig(extend = listOf("wolf")),
) {
    val ctx = NpcTickContext(NpcConstants.live, Random(11L))
    val npcManager = NpcManager(broadcast = {}, getSessions = { emptyList() }, ctxOf = { ctx })
    val events = mutableListOf<PackEvent>()
    val coordinator =
        PackCoordinator(npcManager = npcManager, onEvent = { events += it }).also {
            npcManager.setNpcDamagedByNpcHook(it::onNpcDamagedByNpc)
        }

    init {
        npcManager.loadDefinitions(
            mapOf(
                "wolf" to def("wolf", pack = wolfPack),
                "wolf_veteran" to def("wolf_veteran", pack = veteranPack),
                "cat" to def("cat"),
                "bear" to def("bear", aggroRange = 15f),
            ))
    }

    suspend fun spawn(type: String, x: Float, z: Float): NpcInstance =
        npcManager.spawnNpc("$type@$x,$z", type, Vec3(x, GROUND, z))

    fun typesOf(pack: Pack) =
        pack.memberIds.mapNotNull { npcManager.getInstance(it)?.state?.type }.sorted()
}

class PackCoordinatorTest {

    @Test
    fun call_propagatesThroughRelays() = runBlocking {
        val h = Harness(wolfPack = packConfig(callRadius = 10f))
        h.spawn("bear", 3f, 0f)
        h.spawn("wolf", 0f, 0f) // sees the bear
        h.spawn("wolf", 9f, 0f) // within earshot of the initiator
        h.spawn("wolf", 18f, 0f) // only reachable through the relay

        h.coordinator.tick(1_000L)

        val pack = h.coordinator.activePacks().single()
        assertEquals(3, pack.memberIds.size, "the call should have hopped to the far wolf")
    }

    @Test
    fun relayHops_boundsHowFarTheCallTravels() = runBlocking {
        val h = Harness(wolfPack = packConfig(callRadius = 10f, relayHops = 1))
        h.spawn("bear", 3f, 0f)
        val initiator = h.spawn("wolf", 0f, 0f)
        h.spawn("wolf", 9f, 0f)
        h.spawn("wolf", 18f, 0f)

        h.coordinator.tick(1_000L)

        // The far wolf is one relay too far and ends up calling a pack of its own.
        val pack = assertNotNull(h.coordinator.packOf(initiator.state.id))
        assertEquals(2, pack.memberIds.size)
    }

    @Test
    fun maxSize_capsThePack() = runBlocking {
        val h = Harness(wolfPack = packConfig(maxSize = 3))
        h.spawn("bear", 3f, 0f)
        repeat(6) { i -> h.spawn("wolf", i.toFloat(), 0f) }

        h.coordinator.tick(1_000L)

        // The wolves left over form their own pack rather than overflowing the first one.
        val packs = h.coordinator.activePacks()
        assertEquals(3, packs.first().memberIds.size)
        assertTrue(packs.all { it.memberIds.size <= 3 })
    }

    @Test
    fun extendPackType_recruitsAlliedSpeciesAndIgnoresOthers() = runBlocking {
        val h = Harness(wolfPack = packConfig(extend = listOf("wolf_veteran")))
        h.spawn("bear", 3f, 0f)
        h.spawn("wolf", 0f, 0f)
        h.spawn("wolf_veteran", 5f, 0f)
        h.spawn("cat", 4f, 0f)

        h.coordinator.tick(1_000L)

        val pack = h.coordinator.activePacks().single()
        assertEquals(listOf("wolf", "wolf_veteran"), h.typesOf(pack))
    }

    @Test
    fun emptyExtendPackType_keepsThePackSingleSpecies() = runBlocking {
        val h = Harness(wolfPack = packConfig(extend = emptyList()))
        h.spawn("bear", 3f, 0f)
        // the wolf is the closest, so it leads; the veteran is within earshot but of the wrong type
        val wolf = h.spawn("wolf", 0f, 0f)
        h.spawn("wolf_veteran", 8f, 0f)

        h.coordinator.tick(1_000L)

        val pack = assertNotNull(h.coordinator.packOf(wolf.state.id))
        assertEquals(listOf("wolf"), h.typesOf(pack))
    }

    /**
     * Asymmetric lists are a configuration mistake, documented here: the veteran is recruited but
     * cannot relay the call to the wolves only it can hear.
     */
    @Test
    fun asymmetricExtendPackType_stopsAtTheRecruit() = runBlocking {
        val h =
            Harness(
                wolfPack = packConfig(extend = listOf("wolf_veteran"), callRadius = 10f),
                veteranPack = packConfig(extend = emptyList(), callRadius = 10f),
            )
        h.spawn("bear", 3f, 0f)
        val initiator = h.spawn("wolf", 0f, 0f)
        h.spawn("wolf_veteran", 9f, 0f)
        h.spawn("wolf", 18f, 0f) // only the veteran can hear this one

        h.coordinator.tick(1_000L)

        val pack = assertNotNull(h.coordinator.packOf(initiator.state.id))
        assertEquals(listOf("wolf", "wolf_veteran"), h.typesOf(pack))
    }

    @Test
    fun belowQuorum_theyRallyWithoutStriking() = runBlocking {
        val h = Harness(wolfPack = packConfig(minSizeToEngage = 3))
        val bear = h.spawn("bear", 3f, 0f)
        val wolves = listOf(h.spawn("wolf", 0f, 0f), h.spawn("wolf", 1f, 0f))

        h.coordinator.tick(1_000L)

        val pack = h.coordinator.activePacks().single()
        assertTrue(!pack.engaged, "two wolves must not take on a bear")
        wolves.forEach {
            assertNull(it.npcAggroTarget, "no target before the quorum")
            assertEquals(bear.state.pos, it.packRallyPos)
        }
    }

    @Test
    fun quorumReached_packEngagesAndTargets() = runBlocking {
        val h = Harness(wolfPack = packConfig(minSizeToEngage = 3))
        val bear = h.spawn("bear", 3f, 0f)
        val wolves = List(3) { i -> h.spawn("wolf", i.toFloat(), 0f) }

        h.coordinator.tick(1_000L)

        val pack = h.coordinator.activePacks().single()
        assertTrue(pack.engaged)
        wolves.forEach { assertEquals(bear.state.id, it.npcAggroTarget) }
        assertTrue(h.events.any { it.type == PackEventType.PACK_ENGAGE })
    }

    @Test
    fun rallyTimeout_disbandsAndSilencesTheMembers() = runBlocking {
        val h = Harness(wolfPack = packConfig(minSizeToEngage = 3, rallyTimeoutSec = 5f))
        h.spawn("bear", 3f, 0f)
        val wolf = h.spawn("wolf", 0f, 0f)

        h.coordinator.tick(1_000L)
        assertNotNull(wolf.packId)

        h.coordinator.tick(1_000L + 6_000L)

        assertTrue(h.coordinator.activePacks().isEmpty())
        assertNull(wolf.packId)
        assertNull(wolf.packRallyPos)
        assertTrue(h.events.any { it.type == PackEventType.PACK_DISBAND })

        // still silenced: no new call while the cooldown runs
        h.coordinator.tick(1_000L + 7_000L)
        assertTrue(h.coordinator.activePacks().isEmpty())
    }

    @Test
    fun targetDeath_disbandsThePack() = runBlocking {
        val h = Harness(wolfPack = packConfig(minSizeToEngage = 1))
        val bear = h.spawn("bear", 3f, 0f)
        val wolf = h.spawn("wolf", 0f, 0f)

        h.coordinator.tick(1_000L)
        assertTrue(h.coordinator.activePacks().single().engaged)

        bear.isDead = true
        h.coordinator.tick(2_000L)

        assertTrue(h.coordinator.activePacks().isEmpty())
        assertNull(wolf.packId)
        assertNull(wolf.npcAggroTarget)
    }

    @Test
    fun retaliation_formsAPackAroundTheAggressor() = runBlocking {
        // the bear is out of the wolves' own sight, so only the wound can raise the call
        val h = Harness(wolfPack = packConfig(callRadius = 10f))
        val bear = h.spawn("bear", 40f, 0f)
        val bitten = h.spawn("wolf", 0f, 0f)
        h.spawn("wolf", 5f, 0f)

        h.coordinator.tick(1_000L)
        assertTrue(h.coordinator.activePacks().isEmpty(), "nothing to see, nothing to call about")

        h.npcManager.applyDamage(bitten.state.id, 3, bear.state.id)
        h.coordinator.tick(2_000L)

        val pack = h.coordinator.activePacks().single()
        assertEquals(bear.state.id, pack.targetId)
        assertEquals(2, pack.memberIds.size)
    }

    @Test
    fun packComposition_isStableAcrossRunsWithTheSameLayout() = runBlocking {
        // NPC ids are UUIDs, so the recruits are compared by name — the order is what must not
        // drift between two identical runs.
        suspend fun run(): List<String> {
            val h = Harness()
            // distinct distances on purpose: ties would be broken by UUID, which is not the
            // ordering under test
            h.spawn("bear", 5f, 0f)
            repeat(4) { i -> h.spawn("wolf", i.toFloat(), 0f) }
            h.coordinator.tick(1_000L)
            return h.coordinator.activePacks().single().memberIds.mapNotNull {
                h.npcManager.getInstance(it)?.state?.name
            }
        }
        assertEquals(run(), run())
    }
}
