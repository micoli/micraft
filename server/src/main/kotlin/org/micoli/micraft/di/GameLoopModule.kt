package org.micoli.micraft.di

import java.nio.file.Path
import org.koin.dsl.module
import org.micoli.micraft.buildConfigRegistry
import org.micoli.micraft.combat.AttackRegistryLoader
import org.micoli.micraft.combat.CombatConfigLoader
import org.micoli.micraft.combat.CombatProcessor
import org.micoli.micraft.combat.StatusEffectProcessor
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.npc.NpcConfigLoader
import org.micoli.micraft.npc.NpcManager
import org.micoli.micraft.npc.NpcSpawner
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.rpg.character.DerivedStatsCalculator
import org.micoli.micraft.session.NetworkStats
import org.micoli.micraft.tick.BlockBreaker
import org.micoli.micraft.tick.BlockPlacer
import org.micoli.micraft.tick.ChunkStreamer
import org.micoli.micraft.tick.LiquidManager
import org.micoli.micraft.tick.MovementProcessor
import org.micoli.micraft.tick.VegetationManager
import org.micoli.micraft.trade.TradeConfigLoader
import org.micoli.micraft.trade.TradeManager
import org.micoli.micraft.world.ArmorDefinition
import org.micoli.micraft.world.ArmorRegistryLoader
import org.micoli.micraft.world.BlockRegistryLoader
import org.micoli.micraft.world.ChatChannelManager
import org.micoli.micraft.world.ChatService
import org.micoli.micraft.world.DropConfig
import org.micoli.micraft.world.I18nConfig
import org.micoli.micraft.world.NpcRegistryLoader
import org.micoli.micraft.world.RecipeRegistryLoader
import org.micoli.micraft.world.VegetationConfig
import org.micoli.micraft.world.WeatherConfig
import org.micoli.micraft.world.WeatherManager
import org.micoli.micraft.world.WorldItemManager
import org.micoli.micraft.world.WorldState

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
    single { buildConfigRegistry(get<WeatherConfig>()) }
    single { LiquidManager(get<WorldState>()) }
    single { VegetationConfig(Path.of("data/config/vegetation.yaml")) }
    single {
        VegetationManager(
            get<WorldState>(),
            get<VegetationConfig>(),
            savePath =
                get<OptionalWorldPersistence>().value?.worldDir?.resolve("vegetation_state.json")
                    ?: Path.of("data/world/default_world/vegetation_state.json"),
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
        )
    }
    single { NpcSpawner() }

    // combat cluster — armorRegistry is a mutable snapshot populated later in GameLoop.start(),
    // so (matching prior behavior) these processors are built with an empty map at construction.
    single { CombatConfigLoader(Path.of("data/config/combat.yaml")).load() }
    single { AttackRegistryLoader(Path.of("data/config/attacks")).load() }
    single {
        val emptyArmorRegistry = emptyMap<String, ArmorDefinition>()
        CombatProcessor(
            config = get(),
            attackRegistry = get(),
            armorRegistry = emptyArmorRegistry,
            npcManager = get<NpcManager>(),
            getSessions = get<SessionRegistry>()::all,
            broadcastCombatLog = { msg ->
                val chatMsg =
                    ServerMessage.ChatMessage(channel = "combat", sender = "", message = msg)
                get<SessionRegistry>()
                    .all()
                    .filter { "combat" in it.state.subscribedChannels }
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
                    .filter { "combat" in it.state.subscribedChannels }
                    .forEach { it.send(chatMsg) }
            },
            subscribeToChannel = { session, channel ->
                get<ChatService>().subscribe(session, channel)
            },
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
        )
    }
    single {
        BlockPlacer(
            get<WorldState>(),
            get<SessionRegistry>()::broadcast,
            get<PlayerPersister>()::save,
            get<VegetationManager>(),
            get(),
        )
    }
    single { MovementProcessor(get<WorldState>()) }
    single { ChunkStreamer(get<WorldState>()) }
    single { TerrainCache() }
    single { NetworkStats() }
}
