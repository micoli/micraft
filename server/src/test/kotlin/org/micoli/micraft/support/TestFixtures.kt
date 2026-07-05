package org.micoli.micraft.support

import java.nio.file.Path
import org.micoli.micraft.CommandContext
import org.micoli.micraft.ConfigRegistry
import org.micoli.micraft.auth.AuthProvider
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.npc.NpcManager
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.tick.LiquidManager
import org.micoli.micraft.trade.TradeManager
import org.micoli.micraft.world.ArmorDefinition
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChatChannelManager
import org.micoli.micraft.world.ChatService
import org.micoli.micraft.world.WeatherConfig
import org.micoli.micraft.world.WeatherManager
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.I18nConfig
import org.micoli.micraft.world.ItemDefinition
import org.micoli.micraft.world.ItemRegistry
import org.micoli.micraft.world.ItemType
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldItemManager
import org.micoli.micraft.world.WorldState

private val _itemRegistryInit =
    ItemRegistry.load(
        mapOf(
            ItemType("COBBLESTONE") to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.STONE, label = "COB", bg = "#7A7A7A"),
            ItemType("DIRT") to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.DIRT, label = "DRT", bg = "#8B5A2B"),
            ItemType("SAND") to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.SAND, label = "SND", bg = "#D5C89A"),
            ItemType("GRAVEL") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.GRAVEL,
                    label = "GRV",
                    bg = "#9A9A9A"),
            ItemType("SANDSTONE") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.SANDSTONE,
                    label = "SST",
                    bg = "#C8B46C"),
            ItemType("SNOWBALL") to
                ItemDefinition(buildable = false, label = "SNW", bg = "#DCE8F5"),
            ItemType("FLINT") to ItemDefinition(buildable = false, label = "FLT", bg = "#4A4A52"),
            ItemType("SEED") to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.SEED, label = "SED", bg = "#C8A050"),
            ItemType("GRASS") to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.GRASS, label = "GRS", bg = "#4A7A28"),
            ItemType("SNOW_BLOCK") to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.SNOW, label = "SNB", bg = "#F0F0F0"),
            ItemType("OAK_LOG") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.OAK_LOG,
                    label = "OLG",
                    bg = "#654321"),
            ItemType("OAK_LEAVES") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.OAK_LEAVES,
                    label = "OLV",
                    bg = "#3C641E"),
            ItemType("PINE_LOG") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.PINE_LOG,
                    label = "PLG",
                    bg = "#503219"),
            ItemType("PINE_LEAVES") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.PINE_LEAVES,
                    label = "PLV",
                    bg = "#285A3C"),
            ItemType("PINE_LEAVES_SNOW") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.PINE_LEAVES_SNOW,
                    label = "PLS",
                    bg = "#C8D7DC"),
            ItemType("FLOWER") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.FLOWER,
                    label = "FLW",
                    bg = "#E6C832"),
            ItemType("WEED") to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.WEED, label = "WED", bg = "#468228"),
        ))

fun testPlayerState(
    id: String = "test-id",
    name: String = "Alice",
    pos: Vec3 = Vec3(8f, 8f, 8f),
    orientation: Orientation = Orientation(0f, 0f),
    language: String = "en",
    flying: Boolean = false,
    stance: PlayerStance = PlayerStance.STANDING,
    speedMultiplier: Float = 1f,
    shadersEnabled: Boolean = true,
) =
    PlayerState(
        id = id,
        name = name,
        pos = pos,
        orientation = orientation,
        stance = stance,
        flying = flying,
        speedMultiplier = speedMultiplier,
        language = language,
        shadersEnabled = shadersEnabled,
    )

fun testSession(
    id: String = "test-id",
    name: String = "Alice",
    userName: String = name,
    pos: Vec3 = Vec3(8f, 8f, 8f),
    orientation: Orientation = Orientation(0f, 0f),
    language: String = "en",
    shadersEnabled: Boolean = true,
) =
    FakePlayerSession(
        id,
        userName,
        testPlayerState(
            id = id,
            name = name,
            pos = pos,
            orientation = orientation,
            language = language,
            shadersEnabled = shadersEnabled,
        ))

fun testWorld(vararg solid: Triple<Int, Int, Int>): WorldState {
    val world = WorldState(MapChunkGenerator(solid.associateWith { BlockType.STONE }))
    // Pre-generate referenced chunks so getBlockIfLoaded works in NPC physics tests
    solid
        .map { (x, _, z) ->
            ChunkPos(
                Math.floorDiv(x, WorldConstants.CHUNK_SIZE),
                Math.floorDiv(z, WorldConstants.CHUNK_SIZE))
        }
        .toSet()
        .forEach { world.getOrGenerate(it) }
    return world
}

private val testProjectRoot: Path = Path.of(System.getProperty("projectDir", ".."))

fun testI18n(): I18nConfig =
    I18nConfig.fromClasspath(pluginsRoot = testProjectRoot.resolve("plugins"))

fun testContext(
    world: WorldState = testWorld(),
    sessions: List<PlayerSession> = emptyList(),
    broadcast: suspend (ServerMessage) -> Unit = {},
    kickSession: suspend (String) -> Unit = {},
    reloadConfig: (suspend (String) -> String)? = null,
    savePlayer: (PlayerSession) -> Unit = {},
    worldItems: WorldItemManager? = null,
    npcManager: NpcManager? = null,
    i18n: I18nConfig = testI18n(),
    getGameTime: () -> Long = { 0L },
    setGameTime: (Long) -> Unit = {},
    refetchChunks: (suspend (org.micoli.micraft.session.PlayerSession) -> Unit)? = null,
    liquidManager: LiquidManager? = null,
    configRegistry: ConfigRegistry? = null,
    reloadBlocks: (suspend () -> Unit)? = null,
    reloadNpcs: (suspend () -> Unit)? = null,
    authProvider: AuthProvider? = null,
    groupsConfig: GroupsConfig? = null,
    tradeManager: TradeManager? = null,
    chatService: ChatService? = null,
    chatChannelManager: ChatChannelManager? = null,
    weatherManager: WeatherManager? = null,
    armorRegistry: () -> Map<String, ArmorDefinition> = { emptyMap() },
) =
    CommandContext(
        world = world,
        persistence = null,
        i18n = i18n,
        broadcast = broadcast,
        sessions = { sessions },
        kickSession = kickSession,
        reloadConfig = reloadConfig,
        savePlayer = savePlayer,
        worldItems = worldItems,
        npcManager = npcManager,
        getGameTime = getGameTime,
        setGameTime = setGameTime,
        refetchChunks = refetchChunks,
        liquidManager = liquidManager,
        configRegistry = configRegistry,
        reloadBlocks = reloadBlocks,
        reloadNpcs = reloadNpcs,
        authProvider = authProvider,
        groupsConfig = groupsConfig,
        tradeManager = tradeManager,
        chatService = chatService,
        chatChannelManager = chatChannelManager,
        weatherManager = weatherManager,
        armorRegistry = armorRegistry,
    )

fun testWeatherManager() = WeatherManager(WeatherConfig())
