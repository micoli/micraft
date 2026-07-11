package org.micoli.micraft.di

import java.nio.file.Path
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.game.GameConfig
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.ArmorRegistryLoader
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.classes.ClassesConfig
import org.micoli.micraft.game.classes.ClassesConfigData
import org.micoli.micraft.game.combat.AttackConfig
import org.micoli.micraft.game.combat.CombatConfig
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.combat.RegenProcessor
import org.micoli.micraft.game.combat.SpellConfig
import org.micoli.micraft.game.combat.SpellProcessor
import org.micoli.micraft.game.combat.StatusEffectProcessor
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.npc.NpcConfigLoader
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.npc.NpcSpawner
import org.micoli.micraft.game.recipe.RecipeRegistryLoader
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.rpg.ExperienceConfig
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

val gameLoopModule = module {
    single { SessionRegistry() }
    single { I18nConfig.fromClasspath(pluginsRoot = Path.of("plugins")) }
    single { PlayerPersister(get<OptionalWorldPersistence>().value) }

    // chat cluster
    single { ChatChannelManager() }
    single {
        ChatService(
            get<ChatChannelManager>(), get<PlayerPersister>()::save, get<SessionRegistry>()::all)
    }

    // items / weather / liquid / vegetation cluster
    single { DropConfig(get<BlockRegistryLoader>()) }
    single {
        WorldItemManager(
            get<DropConfig>(),
            broadcast = get<SessionRegistry>()::broadcast,
            savePlayer = get<PlayerPersister>()::save,
            i18n = get<I18nConfig>(),
        )
    }
    single { WeatherConfig() }
    single { WeatherManager(get<WeatherConfig>()) }
    single { ConfigRegistry.buildConfigRegistry(get<WeatherConfig>()) }
    single { LiquidManager(get<WorldState>()) }
    single { VegetationConfig(Path.of("data/config/vegetation.yaml")) }
    single {
        VegetationManager(
            get<WorldState>(),
            get<VegetationConfig>(),
            savePath =
                get<OptionalWorldPersistence>().value?.worldDir?.resolve("vegetation_state.yaml")
                    ?: Path.of("data/world/default_world/vegetation_state.yaml"),
        )
    }

    // npc cluster
    single { RecipeRegistryLoader(Path.of("data/config/recipes.yaml")) }
    single {
        ArmorRegistryLoader(
            armorsPath = Path.of("resources/armors"),
            dataArmorsPath = Path.of("data/resources/armors"),
        )
    }
    single { NpcConfigLoader(Path.of("data/config/npc.yaml")) }
    single {
        NpcRegistryLoader(
            resourcesEntityPath = Path.of("resources/entities"),
            dataEntityPath = Path.of("data/resources/entities"),
        )
    }
    single {
        NpcManager(
            broadcast = get<SessionRegistry>()::broadcast,
            getSessions = get<SessionRegistry>()::all,
            onNpcKilled = get<ExperienceProcessor>()::onNpcKilled,
        )
    }
    single { NpcSpawner() }

    // combat cluster — armorRegistry is a mutable snapshot populated later in GameLoop.start(),
    // so (matching prior behavior) these processors are built with an empty map at construction.
    single { CombatConfig().data }
    single { ExperienceConfig().data }
    single {
        ExperienceProcessor(
            config = get(),
            getSessions = get<SessionRegistry>()::all,
            savePlayer = get<PlayerPersister>()::save,
            subscribeToChannel = { session, channel ->
                get<ChatService>().subscribe(session, channel)
            },
        )
    }
    single(named("attacks")) { AttackConfig().data.attacks }
    single(named("spells")) { SpellConfig().data.spells }
    single {
        val emptyArmorRegistry = emptyMap<String, ArmorDefinition>()
        CombatProcessor(
            config = get(),
            attackRegistry = get(named("attacks")),
            armorRegistry = emptyArmorRegistry,
            classRegistry = get<ClassesConfigData>().classes,
            npcManager = get<NpcManager>(),
            getSessions = get<SessionRegistry>()::all,
            broadcastCombatLog = { msg ->
                val chatMsg =
                    ServerMessage.ChatMessage(channel = "combat", sender = "", message = msg)
                get<SessionRegistry>()
                    .all()
                    .filter { it.state.subscribedChannels.hasChannel("combat") }
                    .forEach { it.send(chatMsg) }
            },
            subscribeToChannel = { session, channel ->
                get<ChatService>().subscribe(session, channel)
            },
            i18n = get<I18nConfig>(),
            savePlayer = get<PlayerPersister>()::save,
        )
    }
    single {
        StatusEffectProcessor(
            armorRegistry = emptyMap<String, ArmorDefinition>(),
            world = get<WorldState>(),
            broadcastHealthUpdate = { id, isNpc, hp, maxHp ->
                get<SessionRegistry>().all().forEach {
                    it.send(ServerMessage.HealthUpdate(id, isNpc, hp, maxHp))
                }
                if (!isNpc) {
                    get<SessionRegistry>()
                        .all()
                        .find { it.id == id }
                        ?.let { s ->
                            val charData = s.characterData
                            if (charData != null) {
                                val derived = DerivedStatsCalculator.compute(charData, emptyList())
                                s.send(
                                    get<CombatProcessor>()
                                        .makeStatusUpdate(
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
                get<SessionRegistry>()
                    .all()
                    .filter { it.state.subscribedChannels.hasChannel("combat") }
                    .forEach { it.send(chatMsg) }
            },
            subscribeToChannel = { session, channel ->
                get<ChatService>().subscribe(session, channel)
            },
        )
    }
    single { ClassesConfig().data }
    single {
        RegenProcessor(
            config = get(),
            maxRage = get<CombatConfigData>().maxRage,
            armorRegistry = emptyMap<String, ArmorDefinition>(),
            combatProcessor = get(),
        )
    }
    single {
        SpellProcessor(
            spellRegistry = get(named("spells")),
            classRegistry = get<ClassesConfigData>().classes,
            armorRegistry = emptyMap<String, ArmorDefinition>(),
            combatConfig = get(),
            combatProcessor = get(),
        )
    }

    // trade cluster
    single { TradeConfigLoader(Path.of("data/config/trade.yaml")) }
    single {
        TradeManager(
            getSessions = get<SessionRegistry>()::all,
            i18n = get<I18nConfig>(),
            savePlayer = get<PlayerPersister>()::save,
            maxDistance = get<TradeConfigLoader>().load().maxDistance,
        )
    }

    // tick cluster
    single {
        BlockBreaker(
            get<WorldState>(),
            get<SessionRegistry>()::broadcast,
            get<WorldItemManager>(),
            get<LiquidManager>(),
            bufferSize = get<GameConfig>().blockBreakBufferSize,
        )
    }
    single {
        BlockPlacer(
            get<WorldState>(),
            get<SessionRegistry>()::broadcast,
            get<PlayerPersister>()::save,
            get<VegetationManager>(),
            get(named("attacks")),
        )
    }
    single { MovementProcessor(get<WorldState>()) }
    single { ChunkStreamer(get<WorldState>()) }
    single { TerrainCache() }
    single { NetworkStats() }
}
