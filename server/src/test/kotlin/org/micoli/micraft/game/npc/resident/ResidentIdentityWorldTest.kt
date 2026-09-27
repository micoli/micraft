package org.micoli.micraft.game.npc.resident

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.world.GameWorld
import org.micoli.micraft.game.world.buildGameWorld
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.EndToEndBoundedChunkGenerator
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testSession

private val shared by lazy { SharedGameServices.default() }

private fun gen(seed: Long = 1234L) =
    EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1, seed = seed)

private val home = Vec3(4.5f, 65f, 4.5f)

private suspend fun townOf(world: GameWorld): List<Resident?> =
    listOf("hermit_man", "seller", "seller").map { type ->
        world.npcManager.spawnNpc("placeholder", type, home).resident
    }

class ResidentIdentityWorldTest {

    @Test
    fun `two builds of the same seed give each Resident the same identity`() = runBlocking {
        val world = buildGameWorld("resident-a", gen(), shared)
        val first = townOf(world)
        val second = townOf(buildGameWorld("resident-b", gen(), shared))

        assertEquals(first, second)
        val regionKey = assertNotNull(world.world.regionAt(home)).key
        val (giver, merchant, secondMerchant) = first.map { assertNotNull(it) }
        assertEquals(ResidentKey(regionKey, "hermit_man", 1), giver.key)
        assertEquals(ResidentKey(regionKey, "seller", 1), merchant.key)
        assertEquals(ResidentKey(regionKey, "seller", 2), secondMerchant.key)
        assertNotEquals(merchant.name, secondMerchant.name)
    }

    @Test
    fun `another seed gives the Residents other names`() = runBlocking {
        val first = townOf(buildGameWorld("resident-c", gen(1234L), shared))
        val second = townOf(buildGameWorld("resident-d", gen(9876L), shared))

        assertNotEquals(first.map { it?.name }, second.map { it?.name })
    }

    @Test
    fun `a Resident is shown under its Resident name`() = runBlocking {
        val world = buildGameWorld("resident-name", gen(), shared)

        val merchant = world.npcManager.spawnNpc("placeholder", "seller", home)

        assertEquals(merchant.resident?.name, merchant.state.name)
    }

    @Test
    fun `a Resident keeps its identity through park and respawn of its Region`() = runBlocking {
        val world = buildGameWorld("resident-park", gen(), shared)
        val merchant = world.npcManager.spawnNpc("placeholder", "seller", home)
        val regionKey = assertNotNull(world.world.regionAt(home)).key

        world.npcManager.park(merchant, regionKey)
        world.npcManager.respawnParked(regionKey)

        val back = world.npcManager.getAll().single { it.state.type == "seller" }
        assertEquals(merchant.resident, back.resident)
        assertEquals(merchant.state.name, back.state.name)
    }

    @Test
    fun `wild NPCs and Pets are not Residents`() = runBlocking {
        val world = buildGameWorld("resident-wild", gen(), shared)
        val player = testSession(pos = home)
        world.onPlayerJoin(player)

        val wolf = world.npcManager.spawnNpc("Grey", "alpha_direwolf", home)
        world.petManager.addTamed(player, "seller", "Pal", 1, 0)
        world.petManager.summon(player, "Pal")

        assertNull(wolf.resident)
        assertNull(world.npcManager.ownedPets().single().resident)
    }

    @Test
    fun `a Resident never takes the name of another NPC or of a Character`() = runBlocking {
        val world = buildGameWorld("resident-unique", gen(), shared)
        val regionKey = assertNotNull(world.world.regionAt(home)).key
        val usual = { rank: Int -> Resident.of(1234L, ResidentKey(regionKey, "seller", rank)).name }
        world.npcManager.spawnNpc(usual(1), "alpha_direwolf", home)
        world.onPlayerJoin(testSession(name = usual(2), pos = home))

        val merchants = List(2) { world.npcManager.spawnNpc("placeholder", "seller", home) }

        assertNotEquals(usual(1), merchants[0].state.name)
        assertNotEquals(usual(2), merchants[1].state.name)
        assertEquals(merchants.map { it.resident?.name }, merchants.map { it.state.name })
    }
}
