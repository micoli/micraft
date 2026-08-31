package org.micoli.micraft.game.world

import java.nio.file.Path
import org.micoli.micraft.di.PlayerPersister
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.FactionsSection
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.combat.RegenProcessor
import org.micoli.micraft.game.combat.SpellProcessor
import org.micoli.micraft.game.combat.StatusEffectProcessor
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcSubsystemFactory
import org.micoli.micraft.game.npc.NpcSubsystemHooks
import org.micoli.micraft.game.pet.PetCoordinator
import org.micoli.micraft.game.pet.PetManager
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.placeable.siege.SiegeProjectileManager
import org.micoli.micraft.game.placeable.siege.SiegeProjectileTickPipeline
import org.micoli.micraft.game.placeable.siege.SiegeWeaponManager
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.rpg.ExperienceProcessor
import org.micoli.micraft.game.social.FactionManager
import org.micoli.micraft.game.social.GroupManager
import org.micoli.micraft.game.social.GuildManager
import org.micoli.micraft.game.social.GuildRegistry
import org.micoli.micraft.game.tick.ChunkStreamer
import org.micoli.micraft.game.tick.IntentCollector
import org.micoli.micraft.game.tick.MovementProcessor
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.vehicle.VehicleManager
import org.micoli.micraft.game.vehicle.VehicleTickPipeline
import org.micoli.micraft.game.world.block.BlockBreaker
import org.micoli.micraft.game.world.block.BlockInteractor
import org.micoli.micraft.game.world.block.BlockPlacer
import org.micoli.micraft.game.world.claim.ClaimRegistry
import org.micoli.micraft.game.world.instance.InstanceRegistry
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.game.world.rail.RailNetworkRegistry
import org.micoli.micraft.game.world.scene.SceneRegistry
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.npc.NpcDeathCause
import org.micoli.micraft.player.hasChannel
import org.micoli.micraft.protocol.ServerMessage

/**
 * Builds a self-contained, memory-only [GameWorld] for a browser E2E session: its own [WorldState]
 * (no persistence), its own copy of every gameplay subsystem, wired with the same production hooks.
 * Additive — it duplicates the wiring the Koin graph does for the default world, the same way
 * `simulation.WorldSimulator` builds its own. Deduplicating the two paths is A9.4.
 */
fun buildE2eGameWorld(
    id: String,
    generator: ChunkGenerator,
    shared: SharedGameServices
): GameWorld {
    val world = WorldState(generator = generator, persistence = null)
    val sessions = SessionRegistry()
    val playerPersister = PlayerPersister(null)
    val chatChannelManager = shared.chatChannelManager
    val chatService = ChatService(chatChannelManager, playerPersister::save, sessions::all)

    val combatLog: suspend (String) -> Unit = { msg ->
        val chatMsg = ServerMessage.ChatMessage(channel = "combat", sender = "", message = msg)
        sessions
            .all()
            .filter { it.state.subscribedChannels.hasChannel("combat") }
            .forEach { it.send(chatMsg) }
    }
    val subscribeToChannel:
        suspend (org.micoli.micraft.game.session.PlayerSession, String) -> Unit =
        { s, c ->
            chatService.subscribe(s, c)
        }

    val experienceProcessor =
        ExperienceProcessor(
            shared.experienceConfigData,
            sessions::all,
            playerPersister::save,
            subscribeToChannel = subscribeToChannel,
            broadcastCombatLog = combatLog,
        )
    val questManager =
        QuestManager(
            getSessions = sessions::all,
            savePlayer = playerPersister::save,
            grantXp = experienceProcessor::grantXp,
            subscribeToChannel = subscribeToChannel,
            i18n = shared.i18n,
        )
    val worldItems =
        WorldItemManager(
            shared.dropConfig,
            broadcast = sessions::broadcast,
            savePlayer = playerPersister::save,
            i18n = shared.i18n,
            onItemCollected = questManager::onItemCollected,
        )
    val weatherManager = WeatherManager(shared.weatherConfig)
    val liquidManager = LiquidManager(world)
    val vegetationManager =
        VegetationManager(
            world,
            shared.vegetationConfig,
            savePath = Path.of("data/world/_e2e_discard/vegetation_state.yaml"),
        )
    val instanceRegistry = InstanceRegistry(null)
    val claimRegistry = ClaimRegistry(null)
    val sceneRegistry = SceneRegistry(null)
    val guildRegistry = GuildRegistry(null)
    val railNetworkRegistry = RailNetworkRegistry(world)
    val vehicleManager = VehicleManager(sessions::broadcast)
    val vehicleTickPipeline = VehicleTickPipeline(vehicleManager)
    val placeableManager = PlaceableManager(sessions::broadcast)
    val siegeWeaponManager = SiegeWeaponManager(sessions::broadcast)
    val siegeProjectileManager = SiegeProjectileManager(sessions::broadcast)
    val siegeProjectileTickPipeline = SiegeProjectileTickPipeline(siegeProjectileManager)

    val factionManager =
        FactionManager(
            getSessions = sessions::all,
            savePlayer = playerPersister::save,
            chatService = chatService,
            channelManager = chatChannelManager,
            i18n = shared.i18n,
            broadcast = sessions::broadcast,
            persistence = null,
        )
    factionManager.applyConfig(FactionsSection())

    val hooks =
        NpcSubsystemHooks(
            broadcast = sessions::broadcast,
            broadcastWorldUpdate = sessions::broadcast,
            getSessions = sessions::all,
            onNpcKilled = { npc, cause, _ ->
                if (cause == NpcDeathCause.KILLED) {
                    experienceProcessor.onNpcKilled(npc)
                    questManager.onNpcKilled(npc)
                    worldItems.spawnNpcLoot(npc.state.pos, npc.definition.loot)
                }
            },
            broadcastCombatLog = combatLog,
            grantNpcKillXp = { predator, prey ->
                experienceProcessor.grantXpToNpcForKill(predator, prey)
            },
        )
    val npcSubsystemFactory =
        NpcSubsystemFactory(
            hooks = hooks,
            world = world,
            vegetationManager = vegetationManager,
            gameDayDurationSecondsOf = { NpcConstants.live.gameDayDurationSeconds },
        )
    val npcManager = npcSubsystemFactory.npcManager

    val combatProcessor =
        CombatProcessor(
            config = shared.combatConfigData,
            attackRegistry = shared.attackRegistry,
            armorRegistry = shared.armorRegistry,
            weaponRegistry = shared.weaponRegistry,
            toolRegistry = shared.toolRegistry,
            classRegistry = shared.classesConfigData.classes,
            npcManager = npcManager,
            vehicleManager = vehicleManager,
            placeableManager = placeableManager,
            getSessions = sessions::all,
            broadcastCombatLog = combatLog,
            subscribeToChannel = subscribeToChannel,
            i18n = shared.i18n,
            savePlayer = playerPersister::save,
        )
    val petCoordinator = PetCoordinator(npcManager, shared.combatConfigData)
    val petManager =
        PetManager(
            npcManager, experienceProcessor, sessions::all, playerPersister::save, shared.i18n)

    val statusEffectProcessor =
        StatusEffectProcessor(
            armorRegistry = shared.armorRegistry,
            weaponRegistry = shared.weaponRegistry,
            toolRegistry = shared.toolRegistry,
            world = world,
            broadcastHealthUpdate = { targetId, isNpc, hp, maxHp ->
                sessions.all().forEach {
                    it.send(ServerMessage.HealthUpdate(targetId, isNpc, hp, maxHp))
                }
                if (!isNpc) {
                    sessions
                        .all()
                        .find { it.id == targetId }
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
                                        s.combatState.attackCooldownsUntilMs,
                                        s.state.godMode))
                            }
                        }
                }
            },
            broadcastCombatLog = combatLog,
            subscribeToChannel = subscribeToChannel,
            onPlayerDowned = { session -> combatProcessor.handlePlayerDowned(session) },
        )
    val regenProcessor =
        RegenProcessor(
            config = shared.classesConfigData,
            maxRage = shared.combatConfigData.maxRage,
            armorRegistry = shared.armorRegistry,
            weaponRegistry = shared.weaponRegistry,
            toolRegistry = shared.toolRegistry,
            combatProcessor = combatProcessor,
        )
    val spellProcessor =
        SpellProcessor(
            spellRegistry = shared.spellRegistry,
            classRegistry = shared.classesConfigData.classes,
            armorRegistry = shared.armorRegistry,
            weaponRegistry = shared.weaponRegistry,
            toolRegistry = shared.toolRegistry,
            combatConfig = shared.combatConfigData,
            combatProcessor = combatProcessor,
            getSessions = sessions::all,
            getNpcs = { npcManager.getAll() },
        )
    val npcSubsystem = npcSubsystemFactory.build(combatProcessor, petCoordinator)

    val blockInteractor =
        BlockInteractor(
            world,
            sessions::broadcast,
            instanceRegistry = instanceRegistry,
            claimRegistry = claimRegistry,
            railNetworkRegistry = railNetworkRegistry,
        )
    val blockBreaker =
        BlockBreaker(
            world,
            sessions::broadcast,
            worldItems,
            liquidManager,
            bufferSize = shared.gameConfig.blockBreakBufferSize,
            instanceRegistry = instanceRegistry,
            claimRegistry = claimRegistry,
            railNetworkRegistry = railNetworkRegistry,
            weaponRegistry = { shared.weaponRegistry },
            toolRegistry = { shared.toolRegistry },
        )
    val blockPlacer =
        BlockPlacer(
            world,
            sessions::broadcast,
            playerPersister::save,
            vegetationManager,
            shared.attackRegistry,
            instanceRegistry = instanceRegistry,
            claimRegistry = claimRegistry,
            railNetworkRegistry = railNetworkRegistry,
            placeableManager = placeableManager,
            siegeWeaponManager = siegeWeaponManager,
        )
    val movementProcessor = MovementProcessor(world)
    val chunkStreamer = ChunkStreamer(world)
    val tradeManager =
        TradeManager(
            getSessions = sessions::all,
            i18n = shared.i18n,
            savePlayer = playerPersister::save,
            maxDistance = shared.tradeConfigLoader.load().maxDistance,
        )
    val claimManager =
        org.micoli.micraft.game.world.claim.ClaimManager(
            registry = claimRegistry,
            config = shared.claimConfigLoader.load(),
            getSessions = sessions::all,
            i18n = shared.i18n,
            savePlayer = playerPersister::save,
            persistence = null,
        )
    val guildManager =
        GuildManager(
            registry = guildRegistry,
            getSessions = sessions::all,
            savePlayer = playerPersister::save,
            chatService = chatService,
            channelManager = chatChannelManager,
            i18n = shared.i18n,
        )
    val groupManager = GroupManager(sessions::all, chatService, chatChannelManager, shared.i18n)

    npcManager.onPetDied = { pet -> petManager.onPetDied(pet) }
    npcManager.onNpcKilledForPets = { killed -> petManager.grantSharedXpForKill(killed) }
    experienceProcessor.onNpcLevelUp = { npc, level ->
        if (npc.ownerId != null) petManager.onPetLevelUp(npc, level)
    }
    chatService.groupMembers = { gid -> groupManager.memberIds(gid) }
    chatService.guildMembers = { gid -> guildRegistry.memberIds(gid) }
    claimRegistry.factionAlly = { actorId, ownerId -> factionManager.sameFaction(actorId, ownerId) }

    val intentCollector =
        IntentCollector(
            blockBreaker,
            blockPlacer,
            onCommand = { _, _ -> },
            blockInteractor = blockInteractor,
            onChatSend = { s, c, t -> chatService.routeMessage(s, c, t) },
            combatProcessor = combatProcessor,
            spellProcessor = spellProcessor,
        )

    return GameWorld(
        id = id,
        world = world,
        persistence = null,
        sessions = sessions,
        terrainCache = TerrainCache(),
        npcManager = npcManager,
        vehicleManager = vehicleManager,
        placeableManager = placeableManager,
        siegeWeaponManager = siegeWeaponManager,
        vegetationManager = vegetationManager,
        gameTimeService = npcSubsystem.gameTimeService,
        npcTickPipeline = npcSubsystem.pipeline,
        vehicleTickPipeline = vehicleTickPipeline,
        siegeProjectileTickPipeline = siegeProjectileTickPipeline,
        siegeProjectileManager = siegeProjectileManager,
        petManager = petManager,
        tradeManager = tradeManager,
        groupManager = groupManager,
        guildManager = guildManager,
        factionManager = factionManager,
        chatService = chatService,
        experienceProcessor = experienceProcessor,
        questManager = questManager,
        mailManager = null,
        claimManager = claimManager,
        sceneRegistry = sceneRegistry,
        playerPersister = playerPersister,
        intentCollectorProvider = { intentCollector },
        blockBreaker = blockBreaker,
        movementProcessor = movementProcessor,
        chunkStreamer = chunkStreamer,
        instanceRegistry = instanceRegistry,
        worldItems = worldItems,
        combatProcessor = combatProcessor,
        statusEffectProcessor = statusEffectProcessor,
        regenProcessor = regenProcessor,
        weatherManager = weatherManager,
        liquidManager = liquidManager,
        auctionManager = null,
        commandContextProvider = { error("e2e GameWorld $id has no CommandContext") },
        pluginTickHandlersProvider = { emptyList() },
        broadcastPlayerAdmin = {},
        appScope = { null },
    )
}
