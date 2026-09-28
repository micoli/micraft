package org.micoli.micraft.di

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import org.koin.ksp.generated.module
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.AttackRankDefinition
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.game.FactionsSection
import org.micoli.micraft.game.GameConfig
import org.micoli.micraft.game.classes.ClassesConfigData
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.equipment.EquipmentCatalog
import org.micoli.micraft.game.equipment.ToolDefinition
import org.micoli.micraft.game.social.FactionManager
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.EquipmentCategory
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.actionblock.ActionBlockRegistry
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.game.world.claim.ClaimRegistry
import org.micoli.micraft.game.world.instance.InstanceRegistry
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.rail.RailNetworkRegistry
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.social.FactionDefinition
import org.micoli.micraft.support.MapChunkGenerator
import org.micoli.micraft.support.testSession

/**
 * Regression coverage for production-only Koin wiring defects (see
 * `.scratch/architecture-review/issues/12-production-wiring-defects.md`): fixtures built from the
 * production [AppModule] Koin graph, not from [org.micoli.micraft.game.GameLoop]'s constructor
 * defaults, which mask these bugs.
 */
class GameLoopModuleTest {

    private fun testChar(id: String, name: String, hp: Int = 20) =
        CharacterData(
            id = id,
            name = name,
            characterClass = CharacterClass.WARRIOR,
            baseStats = BaseStats(str = 30),
            currentHp = hp,
            currentMana = 50,
            currentRage = 50,
        )

    @Test
    fun `combatProcessor built by Koin blocks same-faction friendly fire when disabled`() =
        runBlocking {
            val koin = koinApplication { modules(AppModule().module) }.koin

            // The production FactionManager, resolved through the fixed Koin provider.
            val factionManager = koin.get<FactionManager>()
            factionManager.applyConfig(
                FactionsSection(
                    enabled = true,
                    friendlyFire = false,
                    list = listOf(FactionDefinition(id = "red", name = "Red")),
                ))

            val sessionRegistry = koin.get<SessionRegistry>()
            val attacker = testSession(id = "attacker", name = "Attacker", pos = Vec3(0f, 0f, 0f))
            val target = testSession(id = "target", name = "Target", pos = Vec3(0f, 0f, 0f))
            attacker.characterData = testChar("attacker", "Attacker")
            target.characterData = testChar("target", "Target")
            attacker.state = attacker.state.copy(factionId = "red")
            target.state = target.state.copy(factionId = "red")
            sessionRegistry[attacker.id] = attacker
            sessionRegistry[target.id] = target

            // The exact production provider (`GameLoopModule.combatProcessor`), fed the real
            // Koin-resolved FactionManager — a controlled attack registry keeps the test
            // independent from real game-content config.
            val guaranteedHitAttack =
                AttackDefinition(
                    damageType = DamageType.PHYSICAL,
                    ranks =
                        mapOf(
                            1 to
                                AttackRankDefinition(
                                    power = 0, weaponDice = "1d4", cooldownMs = 1000)))
            val combatProcessor =
                GameLoopModule()
                    .combatProcessor(
                        combatConfigData = koin.get(),
                        characterStats = koin.get(),
                        attacks = mapOf("basic_attack" to guaranteedHitAttack),
                        classesConfigData = ClassesConfigData(),
                        npcManager = koin.get(),
                        vehicleManager = koin.get(),
                        placeableManager = koin.get(),
                        sessionRegistry = sessionRegistry,
                        chatService = koin.get(),
                        i18nConfig = koin.get(),
                        playerPersister = koin.get(),
                        experienceProcessor = koin.get(),
                        experienceConfigData = koin.get(),
                        factionManager = factionManager,
                        rollSource = koin.get(),
                    )

            combatProcessor.handleAttack(
                attacker,
                ClientMessage.AttackTarget(
                    attackId = "basic_attack", targetId = target.id, isNpc = false, attackRank = 1),
            )

            assertEquals(20, target.characterData!!.currentHp, "friendly fire must deal no damage")
        }

    private fun testDropConfig(): DropConfig {
        val resourcesDir = createTempDirectory("resources_blocks")
        val blockDir = resourcesDir.resolve("KOIN_TEST_LOG")
        blockDir.toFile().mkdirs()
        blockDir
            .resolve("KOIN_TEST_LOG.yaml")
            .writeText("hardness: 1\nsolid: true\nminimapColor: [0, 0, 0]\n")
        return DropConfig(BlockRegistryLoader(resourcesDir, createTempDirectory("data_blocks")))
    }

    @Test
    fun `blockBreaker built by Koin reads the live EquipmentCatalog so a reloaded tool applies`() {
        val koin = koinApplication { modules(AppModule().module) }.koin
        val equipmentCatalog = koin.get<EquipmentCatalog>()

        val type = BlockType("KOIN_TEST_LOG")
        BlockRegistry.load(
            mapOf(
                type to
                    BlockDefinition(
                        hardness = 1f,
                        solid = true,
                        requiredEquipment = EquipmentCategory.AXE,
                    )))
        val world = WorldState(MapChunkGenerator(mapOf(Triple(8, 5, 8) to type)))

        val breaker =
            GameLoopModule()
                .blockBreaker(
                    worldState = world,
                    sessionRegistry = SessionRegistry(),
                    worldItemManager = WorldItemManager(testDropConfig(), {}),
                    liquidManager = LiquidManager(world),
                    gameConfig = GameConfig(),
                    instanceRegistry = InstanceRegistry(null),
                    claimRegistry = ClaimRegistry(null),
                    railNetworkRegistry = RailNetworkRegistry(world),
                    actionBlockRegistry = ActionBlockRegistry(null),
                    equipmentCatalog = equipmentCatalog,
                )

        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.state = session.state.copy(leftHandItem = "koin_reloaded_axe")

        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 5, 8)))
        assertNull(session.breakTarget, "unknown tool must not be accepted yet")

        // Simulate `/reload`: the live EquipmentCatalog gains the new tool definition.
        equipmentCatalog.reload(
            tools = mapOf("koin_reloaded_axe" to ToolDefinition(category = EquipmentCategory.AXE)))

        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 5, 8)))
        assertNotNull(session.breakTarget, "reloaded tool must be picked up by BlockBreaker")
    }
}
