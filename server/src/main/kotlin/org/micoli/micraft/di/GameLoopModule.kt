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
import org.micoli.micraft.game.auction.AuctionConfigLoader
import org.micoli.micraft.game.auction.AuctionManager
import org.micoli.micraft.game.auction.AuctionPersistence
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
import org.micoli.micraft.game.equipment.ToolCategoryDefinition
import org.micoli.micraft.game.equipment.ToolCategoryRegistryLoader
import org.micoli.micraft.game.equipment.ToolDefinition
import org.micoli.micraft.game.equipment.ToolRegistryLoader
import org.micoli.micraft.game.equipment.WeaponCategoryDefinition
import org.micoli.micraft.game.equipment.WeaponCategoryRegistryLoader
import org.micoli.micraft.game.equipment.WeaponDefinition
import org.micoli.micraft.game.equipment.WeaponRegistryLoader
import org.micoli.micraft.game.mail.MailManager
import org.micoli.micraft.game.mail.MailPersistence
import org.micoli.micraft.game.npc.NpcConfigLoader
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.npc.NpcSpawner
import org.micoli.micraft.game.npc.NpcSubsystemFactory
import org.micoli.micraft.game.npc.NpcSubsystemHooks
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.placeable.siege.SiegeProjectileManager
import org.micoli.micraft.game.placeable.siege.SiegeWeaponManager
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
import org.micoli.micraft.game.vehicle.VehicleManager
import org.micoli.micraft.game.world.EquipmentCategory
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.block.BlockBreaker
import org.micoli.micraft.game.world.block.BlockInteractor
import org.micoli.micraft.game.world.block.BlockPlacer
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.game.world.instance.InstanceRegistry
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.rail.RailNetworkRegistry
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.game.world.weather.WeatherConfig
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.npc.NpcDeathCause
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

    /**
     * Armour definitions as the combat maths sees them.
     *
     * This snapshot is only used to wire up the other `@Single`s below at DI startup — `GameLoop`
     * keeps its own live `armorRegistry` var, refreshed by `/reload` via the `reloadArmorRegistry`
     * closure (see `GameLoop.kt`), which is what commands/combat actually read after the first
     * reload.
     */
    @Single
    fun armorRegistry(armorRegistryLoader: ArmorRegistryLoader): Map<String, ArmorDefinition> =
        armorRegistryLoader.load()

    @Single
    fun weaponRegistryLoader(): WeaponRegistryLoader =
        WeaponRegistryLoader(
            weaponsPath = Path.of("resources/weapons"),
            dataWeaponsPath = Path.of("data/resources/weapons"),
        )

    @Single
    fun weaponRegistry(weaponRegistryLoader: WeaponRegistryLoader): Map<String, WeaponDefinition> =
        weaponRegistryLoader.load()

    @Single
    fun toolRegistryLoader(): ToolRegistryLoader =
        ToolRegistryLoader(
            toolsPath = Path.of("resources/tools"),
            dataToolsPath = Path.of("data/resources/tools"),
        )

    @Single
    fun toolRegistry(toolRegistryLoader: ToolRegistryLoader): Map<String, ToolDefinition> =
        toolRegistryLoader.load()

    @Single
    fun weaponCategoryRegistryLoader(): WeaponCategoryRegistryLoader =
        WeaponCategoryRegistryLoader(Path.of("data/config/weapons.yaml"))

    @Single
    fun weaponCategories(
        weaponCategoryRegistryLoader: WeaponCategoryRegistryLoader
    ): Map<EquipmentCategory, WeaponCategoryDefinition> = weaponCategoryRegistryLoader.load()

    @Single
    fun toolCategoryRegistryLoader(): ToolCategoryRegistryLoader =
        ToolCategoryRegistryLoader(Path.of("data/config/tools.yaml"))

    @Single
    fun toolCategories(
        toolCategoryRegistryLoader: ToolCategoryRegistryLoader
    ): Map<EquipmentCategory, ToolCategoryDefinition> = toolCategoryRegistryLoader.load()

    @Single
    fun npcConfigLoader(): NpcConfigLoader = NpcConfigLoader(Path.of("data/config/npc.yaml"))

    @Single
    fun npcRegistryLoader(): NpcRegistryLoader =
        NpcRegistryLoader(
            resourcesEntityPath = Path.of("resources/entities"),
            dataEntityPath = Path.of("data/resources/entities"),
        )

    /**
     * The live host's side of the NPC wiring. The admin world simulator builds the same object with
     * its own hooks, which is what makes a simulated run comparable to the real game.
     */
    @Single
    fun npcSubsystemHooks(
        sessionRegistry: SessionRegistry,
        experienceProcessor: ExperienceProcessor,
        questManager: QuestManager,
        worldItemManager: WorldItemManager,
    ): NpcSubsystemHooks =
        NpcSubsystemHooks(
            broadcast = sessionRegistry::broadcast,
            broadcastWorldUpdate = sessionRegistry::broadcast,
            getSessions = sessionRegistry::all,
            // XP, quest credit and loot only for a death someone actually caused: an animal that a
            // player wounded and then outlived must not pay out when it dies of old age or
            // starvation.
            onNpcKilled = { npc, cause, _ ->
                if (cause == NpcDeathCause.KILLED) {
                    experienceProcessor.onNpcKilled(npc)
                    questManager.onNpcKilled(npc)
                    worldItemManager.spawnNpcLoot(npc.state.pos, npc.definition.loot)
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
            grantNpcKillXp = { predator, prey ->
                experienceProcessor.grantXpToNpcForKill(predator, prey)
            },
        )

    @Single
    fun npcSubsystemFactory(
        hooks: NpcSubsystemHooks,
        worldState: WorldState,
        vegetationManager: VegetationManager,
    ): NpcSubsystemFactory =
        NpcSubsystemFactory(
            hooks = hooks,
            world = worldState,
            vegetationManager = vegetationManager,
            // read lazily: npc.yaml is only loaded once GameLoop.start() runs
            gameDayDurationSecondsOf = { NpcConstants.live.gameDayDurationSeconds },
        )

    @Single
    fun npcManager(npcSubsystemFactory: NpcSubsystemFactory): NpcManager =
        npcSubsystemFactory.npcManager

    @Single
    fun npcSpawner(npcSubsystemFactory: NpcSubsystemFactory): NpcSpawner =
        npcSubsystemFactory.npcSpawner

    @Single fun combatConfig(): CombatConfig = CombatConfig()

    @Single fun combatConfigData(combatConfig: CombatConfig): CombatConfigData = combatConfig.data

    @Single fun experienceConfig(): ExperienceConfig = ExperienceConfig()

    @Single
    fun experienceConfigData(experienceConfig: ExperienceConfig): ExperienceConfigData =
        experienceConfig.data

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
            broadcastCombatLog = { msg ->
                val chatMsg =
                    ServerMessage.ChatMessage(channel = "combat", sender = "", message = msg)
                sessionRegistry
                    .all()
                    .filter { it.state.subscribedChannels.hasChannel("combat") }
                    .forEach { it.send(chatMsg) }
            },
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
        armorRegistry: Map<String, ArmorDefinition>,
        weaponRegistry: Map<String, WeaponDefinition>,
        toolRegistry: Map<String, ToolDefinition>,
        @Named("attacks") attacks: Map<String, AttackDefinition>,
        classesConfigData: ClassesConfigData,
        npcManager: NpcManager,
        vehicleManager: VehicleManager,
        placeableManager: PlaceableManager,
        sessionRegistry: SessionRegistry,
        chatService: ChatService,
        i18nConfig: I18nConfig,
        playerPersister: PlayerPersister,
        experienceProcessor: ExperienceProcessor,
        experienceConfigData: ExperienceConfigData,
    ): CombatProcessor =
        CombatProcessor(
            config = combatConfigData,
            attackRegistry = attacks,
            armorRegistry = armorRegistry,
            weaponRegistry = weaponRegistry,
            toolRegistry = toolRegistry,
            classRegistry = classesConfigData.classes,
            npcManager = npcManager,
            vehicleManager = vehicleManager,
            placeableManager = placeableManager,
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
            onPlayerDownedByNpc = { session, killerNpcId ->
                val predator = npcManager.getInstance(killerNpcId) ?: return@CombatProcessor
                val charData = session.characterData ?: return@CombatProcessor
                val xpAmount = experienceConfigData.sources.commonPerLevel * charData.level
                experienceProcessor.grantXpToNpc(predator, xpAmount)
            },
        )

    @Single
    fun statusEffectProcessor(
        armorRegistry: Map<String, ArmorDefinition>,
        weaponRegistry: Map<String, WeaponDefinition>,
        toolRegistry: Map<String, ToolDefinition>,
        worldState: WorldState,
        sessionRegistry: SessionRegistry,
        combatProcessor: CombatProcessor,
        chatService: ChatService,
    ): StatusEffectProcessor =
        StatusEffectProcessor(
            armorRegistry = armorRegistry,
            weaponRegistry = weaponRegistry,
            toolRegistry = toolRegistry,
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
                                        s.combatState.attackCooldownUntilMs,
                                        godMode = s.state.godMode))
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

    @Single fun classesConfig(): ClassesConfig = ClassesConfig()

    @Single
    fun classesConfigData(classesConfig: ClassesConfig): ClassesConfigData = classesConfig.data

    @Single
    fun regenProcessor(
        armorRegistry: Map<String, ArmorDefinition>,
        weaponRegistry: Map<String, WeaponDefinition>,
        toolRegistry: Map<String, ToolDefinition>,
        classesConfigData: ClassesConfigData,
        combatConfigData: CombatConfigData,
        combatProcessor: CombatProcessor,
    ): RegenProcessor =
        RegenProcessor(
            config = classesConfigData,
            maxRage = combatConfigData.maxRage,
            armorRegistry = armorRegistry,
            weaponRegistry = weaponRegistry,
            toolRegistry = toolRegistry,
            combatProcessor = combatProcessor,
        )

    @Single
    fun spellProcessor(
        armorRegistry: Map<String, ArmorDefinition>,
        weaponRegistry: Map<String, WeaponDefinition>,
        toolRegistry: Map<String, ToolDefinition>,
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
            armorRegistry = armorRegistry,
            weaponRegistry = weaponRegistry,
            toolRegistry = toolRegistry,
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
    fun mailManager(
        sessionRegistry: SessionRegistry,
        i18nConfig: I18nConfig,
        playerPersister: PlayerPersister,
        optionalWorldPersistence: OptionalWorldPersistence,
    ): OptionalMailManager =
        OptionalMailManager(
            optionalWorldPersistence.value?.worldDir?.resolve("players")?.let { playersDir ->
                MailManager(
                    persistence = MailPersistence(playersDir),
                    sessionRegistry = sessionRegistry,
                    i18n = i18nConfig,
                    savePlayer = playerPersister::save,
                )
            })

    @Single
    fun auctionConfigLoader(): AuctionConfigLoader =
        AuctionConfigLoader(Path.of("data/config/auction.yaml"))

    @Single
    fun auctionManager(
        sessionRegistry: SessionRegistry,
        i18nConfig: I18nConfig,
        playerPersister: PlayerPersister,
        optionalWorldPersistence: OptionalWorldPersistence,
        optionalMailManager: OptionalMailManager,
        auctionConfigLoader: AuctionConfigLoader,
    ): OptionalAuctionManager =
        OptionalAuctionManager(
            optionalWorldPersistence.value?.worldDir?.let { worldDir ->
                AuctionManager(
                    getSessions = sessionRegistry::all,
                    i18n = i18nConfig,
                    savePlayer = playerPersister::save,
                    persistence = AuctionPersistence(worldDir),
                    mailManager = optionalMailManager.value,
                    config = auctionConfigLoader.load(),
                )
            })

    @Single
    fun railNetworkRegistry(worldState: WorldState): RailNetworkRegistry =
        RailNetworkRegistry(worldState)

    @Single
    fun vehicleManager(sessionRegistry: SessionRegistry): VehicleManager =
        VehicleManager(sessionRegistry::broadcast)

    @Single
    fun placeableManager(sessionRegistry: SessionRegistry): PlaceableManager =
        PlaceableManager(sessionRegistry::broadcast)

    @Single
    fun siegeWeaponManager(sessionRegistry: SessionRegistry): SiegeWeaponManager =
        SiegeWeaponManager(sessionRegistry::broadcast)

    @Single
    fun siegeProjectileManager(sessionRegistry: SessionRegistry): SiegeProjectileManager =
        SiegeProjectileManager(sessionRegistry::broadcast)

    @Single
    fun blockInteractor(
        worldState: WorldState,
        sessionRegistry: SessionRegistry,
        instanceRegistry: InstanceRegistry,
        railNetworkRegistry: RailNetworkRegistry,
    ): BlockInteractor =
        BlockInteractor(
            worldState,
            sessionRegistry::broadcast,
            instanceRegistry = instanceRegistry,
            railNetworkRegistry = railNetworkRegistry,
        )

    @Single
    fun blockBreaker(
        worldState: WorldState,
        sessionRegistry: SessionRegistry,
        worldItemManager: WorldItemManager,
        liquidManager: LiquidManager,
        gameConfig: GameConfig,
        instanceRegistry: InstanceRegistry,
        railNetworkRegistry: RailNetworkRegistry,
        weaponRegistry: Map<String, WeaponDefinition>,
        toolRegistry: Map<String, ToolDefinition>,
    ): BlockBreaker =
        BlockBreaker(
            worldState,
            sessionRegistry::broadcast,
            worldItemManager,
            liquidManager,
            bufferSize = gameConfig.blockBreakBufferSize,
            instanceRegistry = instanceRegistry,
            railNetworkRegistry = railNetworkRegistry,
            weaponRegistry = { weaponRegistry },
            toolRegistry = { toolRegistry },
        )

    @Single
    fun blockPlacer(
        worldState: WorldState,
        sessionRegistry: SessionRegistry,
        playerPersister: PlayerPersister,
        vegetationManager: VegetationManager,
        @Named("attacks") attacks: Map<String, AttackDefinition>,
        instanceRegistry: InstanceRegistry,
        railNetworkRegistry: RailNetworkRegistry,
        placeableManager: PlaceableManager,
        siegeWeaponManager: SiegeWeaponManager,
    ): BlockPlacer =
        BlockPlacer(
            worldState,
            sessionRegistry::broadcast,
            playerPersister::save,
            vegetationManager,
            attacks,
            instanceRegistry = instanceRegistry,
            railNetworkRegistry = railNetworkRegistry,
            placeableManager = placeableManager,
            siegeWeaponManager = siegeWeaponManager,
        )

    @Single
    fun movementProcessor(worldState: WorldState): MovementProcessor = MovementProcessor(worldState)

    @Single fun chunkStreamer(worldState: WorldState): ChunkStreamer = ChunkStreamer(worldState)

    @Single fun terrainCache(): TerrainCache = TerrainCache()

    @Single fun networkStats(): NetworkStats = NetworkStats()
}
