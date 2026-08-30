package org.micoli.micraft.game

import java.nio.file.Path
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.ArmorRegistryLoader
import org.micoli.micraft.game.auction.AuctionConfigLoader
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.classes.ClassesConfig
import org.micoli.micraft.game.classes.ClassesConfigData
import org.micoli.micraft.game.combat.CombatConfig
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.SkillsConfig
import org.micoli.micraft.game.combat.SpellDefinition
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.equipment.ToolCategoryDefinition
import org.micoli.micraft.game.equipment.ToolCategoryRegistryLoader
import org.micoli.micraft.game.equipment.ToolDefinition
import org.micoli.micraft.game.equipment.ToolRegistryLoader
import org.micoli.micraft.game.equipment.WeaponCategoryDefinition
import org.micoli.micraft.game.equipment.WeaponCategoryRegistryLoader
import org.micoli.micraft.game.equipment.WeaponDefinition
import org.micoli.micraft.game.equipment.WeaponRegistryLoader
import org.micoli.micraft.game.npc.NpcConfigLoader
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.quest.QuestRegistryLoader
import org.micoli.micraft.game.recipe.RecipeRegistryLoader
import org.micoli.micraft.game.rpg.ExperienceConfig
import org.micoli.micraft.game.rpg.ExperienceConfigData
import org.micoli.micraft.game.session.NetworkStats
import org.micoli.micraft.game.trade.TradeConfigLoader
import org.micoli.micraft.game.world.EquipmentCategory
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.game.world.claim.ClaimConfigLoader
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.weather.WeatherConfig

/**
 * JVM-global services shared by every [org.micoli.micraft.game.world.GameWorld]: stateless config,
 * classpath-derived registries and process-wide counters. Grouping them keeps the per-world
 * bundle's constructor readable, mirroring `simulation.SimulationDeps`.
 *
 * Nothing here is world- or session-scoped — a value that mutates per world (a `WorldState`, a
 * session list, an NPC manager) belongs on `GameWorld`, not here.
 */
class SharedGameServices(
    val gameConfig: GameConfig,
    val i18n: I18nConfig,
    val configRegistry: ConfigRegistry,
    val weatherConfig: WeatherConfig,
    val vegetationConfig: VegetationConfig,
    val chatChannelManager: ChatChannelManager,
    val dropConfig: DropConfig,
    val networkStats: NetworkStats,
    val recipeRegistryLoader: RecipeRegistryLoader,
    val armorRegistryLoader: ArmorRegistryLoader,
    val weaponRegistryLoader: WeaponRegistryLoader,
    val toolRegistryLoader: ToolRegistryLoader,
    val weaponCategoryRegistryLoader: WeaponCategoryRegistryLoader,
    val toolCategoryRegistryLoader: ToolCategoryRegistryLoader,
    val npcConfigLoader: NpcConfigLoader,
    val npcRegistryLoader: NpcRegistryLoader,
    val questRegistryLoader: QuestRegistryLoader,
    val tradeConfigLoader: TradeConfigLoader,
    val auctionConfigLoader: AuctionConfigLoader,
    val claimConfigLoader: ClaimConfigLoader,
    val combatConfig: CombatConfig,
    val skillsConfig: SkillsConfig,
    val classesConfig: ClassesConfig,
    val experienceConfig: ExperienceConfig,
    val armorRegistry: Map<String, ArmorDefinition>,
    val weaponRegistry: Map<String, WeaponDefinition>,
    val toolRegistry: Map<String, ToolDefinition>,
    val weaponCategories: Map<EquipmentCategory, WeaponCategoryDefinition>,
    val toolCategories: Map<EquipmentCategory, ToolCategoryDefinition>,
    val attackRegistry: Map<String, AttackDefinition>,
    val spellRegistry: Map<String, SpellDefinition>,
    val combatConfigData: CombatConfigData,
    val classesConfigData: ClassesConfigData,
    val experienceConfigData: ExperienceConfigData,
) {
    companion object {
        /**
         * Loads every shared service straight from disk — the default for tests and non-Koin
         * callers.
         */
        fun default(): SharedGameServices {
            val weatherConfig = WeatherConfig()
            val armorLoader =
                ArmorRegistryLoader(
                    armorsPath = Path.of("resources/armors"),
                    dataArmorsPath = Path.of("data/resources/armors"))
            val weaponLoader =
                WeaponRegistryLoader(
                    weaponsPath = Path.of("resources/weapons"),
                    dataWeaponsPath = Path.of("data/resources/weapons"))
            val toolLoader =
                ToolRegistryLoader(
                    toolsPath = Path.of("resources/tools"),
                    dataToolsPath = Path.of("data/resources/tools"))
            val weaponCatLoader = WeaponCategoryRegistryLoader(Path.of("data/config/weapons.yaml"))
            val toolCatLoader = ToolCategoryRegistryLoader(Path.of("data/config/tools.yaml"))
            val skills = SkillsConfig()
            val combat = CombatConfig()
            val classes = ClassesConfig()
            val experience = ExperienceConfig()
            return SharedGameServices(
                gameConfig = GameConfig(),
                i18n = I18nConfig.fromClasspath(pluginsRoot = Path.of("plugins")),
                configRegistry = ConfigRegistry.buildConfigRegistry(weatherConfig),
                weatherConfig = weatherConfig,
                vegetationConfig = VegetationConfig(Path.of("data/config/vegetation.yaml")),
                chatChannelManager = ChatChannelManager(),
                dropConfig =
                    DropConfig(
                        BlockRegistryLoader(
                            Path.of("resources/blocks"), Path.of("data/resources/blocks"))),
                networkStats = NetworkStats(),
                recipeRegistryLoader = RecipeRegistryLoader(Path.of("data/config/recipes.yaml")),
                armorRegistryLoader = armorLoader,
                weaponRegistryLoader = weaponLoader,
                toolRegistryLoader = toolLoader,
                weaponCategoryRegistryLoader = weaponCatLoader,
                toolCategoryRegistryLoader = toolCatLoader,
                npcConfigLoader = NpcConfigLoader(Path.of("data/config/npc.yaml")),
                npcRegistryLoader =
                    NpcRegistryLoader(
                        resourcesEntityPath = Path.of("resources/entities"),
                        dataEntityPath = Path.of("data/resources/entities")),
                questRegistryLoader = QuestRegistryLoader(Path.of("resources/quests")),
                tradeConfigLoader = TradeConfigLoader(Path.of("data/config/trade.yaml")),
                auctionConfigLoader = AuctionConfigLoader(Path.of("data/config/auction.yaml")),
                claimConfigLoader = ClaimConfigLoader(Path.of("data/config/claims.yaml")),
                combatConfig = combat,
                skillsConfig = skills,
                classesConfig = classes,
                experienceConfig = experience,
                armorRegistry = armorLoader.load(),
                weaponRegistry = weaponLoader.load(),
                toolRegistry = toolLoader.load(),
                weaponCategories = weaponCatLoader.load(),
                toolCategories = toolCatLoader.load(),
                attackRegistry = skills.data.attacks,
                spellRegistry = skills.data.spells,
                combatConfigData = combat.data,
                classesConfigData = classes.data,
                experienceConfigData = experience.data,
            )
        }
    }
}
