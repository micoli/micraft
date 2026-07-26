package org.micoli.micraft.di

import java.nio.file.Path
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.game.GameConfig
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.ArmorRegistryLoader
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.classes.ClassesConfig
import org.micoli.micraft.game.classes.ClassesConfigData
import org.micoli.micraft.game.combat.CombatConfig
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.combat.RegenProcessor
import org.micoli.micraft.game.combat.SkillsConfig
import org.micoli.micraft.game.combat.SpellDefinition
import org.micoli.micraft.game.combat.SpellProcessor
import org.micoli.micraft.game.combat.StatusEffectProcessor
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.npc.NpcConfigLoader
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.npc.NpcSpawner
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestRegistryLoader
import org.micoli.micraft.game.recipe.RecipeRegistryLoader
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.rpg.ExperienceConfig
import org.micoli.micraft.game.rpg.ExperienceConfigData
import org.micoli.micraft.game.rpg.ExperienceProcessor
import org.micoli.micraft.game.session.NetworkStats
import org.micoli.micraft.game.tick.ChunkStreamer
import org.micoli.micraft.game.tick.MovementProcessor
import org.micoli.micraft.game.trade.TradeConfigLoader
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.block.BlockBreaker
import org.micoli.micraft.game.world.block.BlockPlacer
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.game.world.weather.WeatherConfig
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.player.hasChannel
import org.micoli.micraft.protocol.ServerMessage

@Module
class GameLoopModule {
    @Single fun sessionRegistry(): SessionRegistry = SessionRegistry()

    @Single
    fun i18nConfig(): I18nConfig = I18nConfig.fromClasspath(pluginsRoot = Path.of("plugins"))

    @Single
    fun playerPersister(optionalWorldPersistence: OptionalWorldPersistence): PlayerPersister =
        PlayerPersister(optionalWorldPersistence.value)

    @Single fun chatChannelManager(): ChatChannelManager = ChatChannelManager()

    @Single
    fun chatService(
        chatChannelManager: ChatChannelManager,
        playerPersister: PlayerPersister,
        sessionRegistry: SessionRegistry,
    ): ChatService = ChatService(chatChannelManager, playerPersister::save, sessionRegistry::all)

    @Single
    fun dropConfig(blockRegistryLoader: BlockRegistryLoader): DropConfig =
        DropConfig(blockRegistryLoader)

    @Single
    fun questRegistryLoader(): QuestRegistryLoader =
        QuestRegistryLoader(Path.of("resources/quests"))

    @Single
    fun questManager(
        sessionRegistry: SessionRegistry,
        playerPersister: PlayerPersister,
        experienceProcessor: ExperienceProcessor,
        chatService: ChatService,
        i18nConfig: I18nConfig,
    ): QuestManager =
        QuestManager(
            getSessions = sessionRegistry::all,
            savePlayer = playerPersister::save,
            grantXp = experienceProcessor::grantXp,
            subscribeToChannel = { session, channel -> chatService.subscribe(session, channel) },
            i18n = i18nConfig,
        )

    @Single
    fun worldItemManager(
        dropConfig: DropConfig,
        sessionRegistry: SessionRegistry,
        playerPersister: PlayerPersister,
        i18nConfig: I18nConfig,
        questManager: QuestManager,
    ): WorldItemManager =
        WorldItemManager(
            dropConfig,
            broadcast = sessionRegistry::broadcast,
            savePlayer = playerPersister::save,
            i18n = i18nConfig,
            onItemCollected = questManager::onItemCollected,
        )

    @Single fun weatherConfig(): WeatherConfig = WeatherConfig()

    @Single
    fun weatherManager(weatherConfig: WeatherConfig): WeatherManager = WeatherManager(weatherConfig)

    @Single
    fun configRegistry(weatherConfig: WeatherConfig): ConfigRegistry =
        ConfigRegistry.buildConfigRegistry(weatherConfig)

    @Single fun liquidManager(worldState: WorldState): LiquidManager = LiquidManager(worldState)

    @Single
    fun vegetationConfig(): VegetationConfig =
        VegetationConfig(Path.of("data/config/vegetation.yaml"))

    @Single
    fun vegetationManager(
        worldState: WorldState,
        vegetationConfig: VegetationConfig,
        optionalWorldPersistence: OptionalWorldPersistence,
    ): VegetationManager =
        VegetationManager(
            worldState,
            vegetationConfig,
            savePath =
                optionalWorldPersistence.value?.worldDir?.resolve("vegetation_state.yaml")
                    ?: Path.of("data/world/default_world/vegetation_state.yaml"),
        )

    @Single
    fun recipeRegistryLoader(): RecipeRegistryLoader =
        RecipeRegistryLoader(Path.of("data/config/recipes.yaml"))

    @Single
    fun armorRegistryLoader(): ArmorRegistryLoader =
        ArmorRegistryLoader(
            armorsPath = Path.of("resources/armors"),
            dataArmorsPath = Path.of("data/resources/armors"),
        )

    @Single
    fun npcConfigLoader(): NpcConfigLoader = NpcConfigLoader(Path.of("data/config/npc.yaml"))

    @Single
    fun npcRegistryLoader(): NpcRegistryLoader =
        NpcRegistryLoader(
            resourcesEntityPath = Path.of("resources/entities"),
            dataEntityPath = Path.of("data/resources/entities"),
        )

    @Single
    fun npcManager(
        sessionRegistry: SessionRegistry,
        experienceProcessor: ExperienceProcessor,
        questManager: QuestManager,
    ): NpcManager =
        NpcManager(
            broadcast = sessionRegistry::broadcast,
            getSessions = sessionRegistry::all,
            onNpcKilled = { npc ->
                experienceProcessor.onNpcKilled(npc)
                questManager.onNpcKilled(npc)
            },
            broadcastCombatLog = { msg ->
                val chatMsg =
                    ServerMessage.ChatMessage(channel = "combat", sender = "", message = msg)
                sessionRegistry
                    .all()
                    .filter { it.state.subscribedChannels.hasChannel("combat") }
                    .forEach { it.send(chatMsg) }
            },
        )

    @Single fun npcSpawner(): NpcSpawner = NpcSpawner()

    @Single fun combatConfigData(): CombatConfigData = CombatConfig().data

    @Single fun experienceConfigData(): ExperienceConfigData = ExperienceConfig().data

    @Single
    fun experienceProcessor(
        experienceConfigData: ExperienceConfigData,
        sessionRegistry: SessionRegistry,
        playerPersister: PlayerPersister,
        chatService: ChatService,
    ): ExperienceProcessor =
        ExperienceProcessor(
            config = experienceConfigData,
            getSessions = sessionRegistry::all,
            savePlayer = playerPersister::save,
            subscribeToChannel = { session, channel -> chatService.subscribe(session, channel) },
        )

    @Single fun skillsConfig(): SkillsConfig = SkillsConfig()

    @Single
    @Named("attacks")
    fun attacks(skillsConfig: SkillsConfig): Map<String, AttackDefinition> =
        skillsConfig.data.attacks

    @Single
    @Named("spells")
    fun spells(skillsConfig: SkillsConfig): Map<String, SpellDefinition> = skillsConfig.data.spells

    @Single
    fun combatProcessor(
        combatConfigData: CombatConfigData,
        @Named("attacks") attacks: Map<String, AttackDefinition>,
        classesConfigData: ClassesConfigData,
        npcManager: NpcManager,
        sessionRegistry: SessionRegistry,
        chatService: ChatService,
        i18nConfig: I18nConfig,
        playerPersister: PlayerPersister,
    ): CombatProcessor =
        CombatProcessor(
            config = combatConfigData,
            attackRegistry = attacks,
            armorRegistry = emptyMap<String, ArmorDefinition>(),
            classRegistry = classesConfigData.classes,
            npcManager = npcManager,
            getSessions = sessionRegistry::all,
            broadcastCombatLog = { msg ->
                val chatMsg =
                    ServerMessage.ChatMessage(channel = "combat", sender = "", message = msg)
                sessionRegistry
                    .all()
                    .filter { it.state.subscribedChannels.hasChannel("combat") }
                    .forEach { it.send(chatMsg) }
            },
            subscribeToChannel = { session, channel -> chatService.subscribe(session, channel) },
            i18n = i18nConfig,
            savePlayer = playerPersister::save,
        )

    @Single
    fun statusEffectProcessor(
        worldState: WorldState,
        sessionRegistry: SessionRegistry,
        combatProcessor: CombatProcessor,
        chatService: ChatService,
    ): StatusEffectProcessor =
        StatusEffectProcessor(
            armorRegistry = emptyMap<String, ArmorDefinition>(),
            world = worldState,
            broadcastHealthUpdate = { id, isNpc, hp, maxHp ->
                sessionRegistry.all().forEach {
                    it.send(ServerMessage.HealthUpdate(id, isNpc, hp, maxHp))
                }
                if (!isNpc) {
                    sessionRegistry
                        .all()
                        .find { it.id == id }
                        ?.let { s ->
                            val charData = s.characterData
                            if (charData != null) {
                                val derived = DerivedStatsCalculator.compute(charData, emptyList())
                                s.send(
                                    combatProcessor.makeStatusUpdate(
                                        charData,
                                        derived,
                                        s.state.stance,
                                        s.combatState.attackCooldownUntilMs))
                            }
                        }
                }
            },
            broadcastCombatLog = { msg ->
                val chatMsg =
                    ServerMessage.ChatMessage(channel = "combat", sender = "", message = msg)
                sessionRegistry
                    .all()
                    .filter { it.state.subscribedChannels.hasChannel("combat") }
                    .forEach { it.send(chatMsg) }
            },
            subscribeToChannel = { session, channel -> chatService.subscribe(session, channel) },
            onPlayerDowned = { session -> combatProcessor.handlePlayerDowned(session) },
        )

    @Single fun classesConfigData(): ClassesConfigData = ClassesConfig().data

    @Single
    fun regenProcessor(
        classesConfigData: ClassesConfigData,
        combatConfigData: CombatConfigData,
        combatProcessor: CombatProcessor,
    ): RegenProcessor =
        RegenProcessor(
            config = classesConfigData,
            maxRage = combatConfigData.maxRage,
            armorRegistry = emptyMap<String, ArmorDefinition>(),
            combatProcessor = combatProcessor,
        )

    @Single
    fun spellProcessor(
        @Named("spells") spells: Map<String, SpellDefinition>,
        classesConfigData: ClassesConfigData,
        combatConfigData: CombatConfigData,
        combatProcessor: CombatProcessor,
        sessionRegistry: SessionRegistry,
        npcManager: NpcManager,
    ): SpellProcessor =
        SpellProcessor(
            spellRegistry = spells,
            classRegistry = classesConfigData.classes,
            armorRegistry = emptyMap<String, ArmorDefinition>(),
            combatConfig = combatConfigData,
            combatProcessor = combatProcessor,
            getSessions = sessionRegistry::all,
            getNpcs = { npcManager.getAll() },
        )

    @Single
    fun tradeConfigLoader(): TradeConfigLoader =
        TradeConfigLoader(Path.of("data/config/trade.yaml"))

    @Single
    fun tradeManager(
        sessionRegistry: SessionRegistry,
        i18nConfig: I18nConfig,
        playerPersister: PlayerPersister,
        tradeConfigLoader: TradeConfigLoader,
    ): TradeManager =
        TradeManager(
            getSessions = sessionRegistry::all,
            i18n = i18nConfig,
            savePlayer = playerPersister::save,
            maxDistance = tradeConfigLoader.load().maxDistance,
        )

    @Single
    fun blockBreaker(
        worldState: WorldState,
        sessionRegistry: SessionRegistry,
        worldItemManager: WorldItemManager,
        liquidManager: LiquidManager,
        gameConfig: GameConfig,
    ): BlockBreaker =
        BlockBreaker(
            worldState,
            sessionRegistry::broadcast,
            worldItemManager,
            liquidManager,
            bufferSize = gameConfig.blockBreakBufferSize,
        )

    @Single
    fun blockPlacer(
        worldState: WorldState,
        sessionRegistry: SessionRegistry,
        playerPersister: PlayerPersister,
        vegetationManager: VegetationManager,
        @Named("attacks") attacks: Map<String, AttackDefinition>,
    ): BlockPlacer =
        BlockPlacer(
            worldState,
            sessionRegistry::broadcast,
            playerPersister::save,
            vegetationManager,
            attacks,
        )

    @Single
    fun movementProcessor(worldState: WorldState): MovementProcessor = MovementProcessor(worldState)

    @Single fun chunkStreamer(worldState: WorldState): ChunkStreamer = ChunkStreamer(worldState)

    @Single fun terrainCache(): TerrainCache = TerrainCache()

    @Single fun networkStats(): NetworkStats = NetworkStats()
}
