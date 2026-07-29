package org.micoli.micraft.support

import java.nio.file.Path
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.auth.AuthProvider
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.ItemDefinition
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.weather.WeatherConfig
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage

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
            ItemType("LEGO_BRICK") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.LEGO_BRICK,
                    label = "LGO",
                    bg = "#DC3232"),
            ItemType("LEGO_SLOPE") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.LEGO_SLOPE,
                    label = "LSL",
                    bg = "#B43232"),
            ItemType("LEGO_PLATE") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.LEGO_PLATE,
                    label = "LPL",
                    bg = "#C86432"),
            ItemType("LEGO_BRICK_2X1") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.LEGO_BRICK_2X1,
                    label = "L2X",
                    bg = "#3264DC"),
            ItemType("LEGO_BRICK_1X2") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.LEGO_BRICK_1X2,
                    label = "L1X",
                    bg = "#32DC64"),
            ItemType("LEGO_PLATE_2X2") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.LEGO_PLATE_2X2,
                    label = "P22",
                    bg = "#B450B4"),
            ItemType("LEGO_PLATE_2X4") to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.LEGO_PLATE_2X4,
                    label = "P24",
                    bg = "#50B450"),
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
    refetchChunks: (suspend (PlayerSession) -> Unit)? = null,
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
    namedPoints: () -> Map<String, Vec3> = { emptyMap() },
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
        namedPoints = namedPoints,
    )

fun testWeatherManager() = WeatherManager(WeatherConfig())
