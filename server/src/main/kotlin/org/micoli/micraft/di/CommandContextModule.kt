package org.micoli.micraft.di

import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.equipment.ToolCategoryDefinition
import org.micoli.micraft.game.equipment.ToolDefinition
import org.micoli.micraft.game.equipment.WeaponCategoryDefinition
import org.micoli.micraft.game.equipment.WeaponDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.vehicle.VehicleManager
import org.micoli.micraft.game.world.EquipmentCategory
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.claim.ClaimManager
import org.micoli.micraft.game.world.claim.ClaimRegistry
import org.micoli.micraft.game.world.instance.InstanceRegistry
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.proceduralGenerator.ProceduralChunkGenerator
import org.micoli.micraft.game.world.scene.SceneRegistry
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage

data class CommandContextClosures(
    val broadcast: suspend (ServerMessage) -> Unit,
    val sessions: () -> Collection<PlayerSession>,
    val kickSession: suspend (String) -> Unit,
    val reloadConfig: (suspend (lang: String) -> String)?,
    val commands: () -> Collection<CommandHandler>,
    val savePlayer: (PlayerSession) -> Unit,
    val getGameTime: () -> Long,
    val setGameTime: (Long) -> Unit,
    val refetchChunks: (suspend (PlayerSession) -> Unit)?,
    val flushWorld: (() -> Unit)?,
    val reloadBlocks: (suspend () -> Unit)?,
    val reloadNpcs: (suspend () -> Unit)?,
    val reloadRbac: (() -> Unit)?,
    val armorRegistry: () -> Map<String, ArmorDefinition>,
    val weaponRegistry: () -> Map<String, WeaponDefinition>,
    val toolRegistry: () -> Map<String, ToolDefinition>,
    val weaponCategories: () -> Map<EquipmentCategory, WeaponCategoryDefinition>,
    val toolCategories: () -> Map<EquipmentCategory, ToolCategoryDefinition>,
    val applyBuff: suspend (PlayerSession, StatusEffect, Float) -> Unit,
)

@Module
class CommandContextModule {
    @Single
    fun commandContext(
        @InjectedParam closures: CommandContextClosures,
        worldState: WorldState,
        optionalWorldPersistence: OptionalWorldPersistence,
        i18nConfig: I18nConfig,
        worldItemManager: WorldItemManager,
        npcManager: NpcManager,
        chatService: ChatService,
        chatChannelManager: ChatChannelManager,
        weatherManager: WeatherManager,
        optionalAuthProvider: OptionalAuthProvider,
        groupsConfig: GroupsConfig,
        liquidManager: LiquidManager,
        configRegistry: ConfigRegistry,
        tradeManager: TradeManager,
        optionalAuctionManager: OptionalAuctionManager,
        questManager: QuestManager,
        instanceRegistry: InstanceRegistry,
        claimRegistry: ClaimRegistry,
        claimManager: ClaimManager,
        vehicleManager: VehicleManager,
        sceneRegistry: SceneRegistry,
    ): CommandContext {
        val generator = worldState.generator as? ProceduralChunkGenerator
        val cavernPoints = generator?.namedCavernPoints() ?: emptyMap()
        val staircasePoints = generator?.namedStaircasePoints() ?: emptyMap()
        fun instanceNamedPoints(): Map<String, Vec3> =
            instanceRegistry.all().associate { zone ->
                val avgCx = zone.chunks.map { it.cx }.average()
                val avgCz = zone.chunks.map { it.cz }.average()
                "instance - ${zone.name}" to
                    Vec3(
                        Math.round(avgCx).toInt() * WorldConstants.CHUNK_SIZE +
                            WorldConstants.CHUNK_SIZE / 2f,
                        zone.yMin.toFloat(),
                        Math.round(avgCz).toInt() * WorldConstants.CHUNK_SIZE +
                            WorldConstants.CHUNK_SIZE / 2f,
                    )
            }
        return CommandContext(
            world = worldState,
            persistence = optionalWorldPersistence.value,
            i18n = i18nConfig,
            broadcast = closures.broadcast,
            sessions = closures.sessions,
            kickSession = closures.kickSession,
            reloadConfig = closures.reloadConfig,
            commands = closures.commands,
            savePlayer = closures.savePlayer,
            worldItems = worldItemManager,
            npcManager = npcManager,
            getGameTime = closures.getGameTime,
            setGameTime = closures.setGameTime,
            refetchChunks = closures.refetchChunks,
            flushWorld = closures.flushWorld,
            chatService = chatService,
            chatChannelManager = chatChannelManager,
            weatherManager = weatherManager,
            authProvider = optionalAuthProvider.value,
            groupsConfig = groupsConfig,
            liquidManager = liquidManager,
            configRegistry = configRegistry,
            reloadBlocks = closures.reloadBlocks,
            reloadNpcs = closures.reloadNpcs,
            reloadRbac = closures.reloadRbac,
            armorRegistry = closures.armorRegistry,
            weaponRegistry = closures.weaponRegistry,
            toolRegistry = closures.toolRegistry,
            weaponCategories = closures.weaponCategories,
            toolCategories = closures.toolCategories,
            tradeManager = tradeManager,
            auctionManager = optionalAuctionManager.value,
            questManager = questManager,
            namedPoints = { cavernPoints + staircasePoints + instanceNamedPoints() },
            applyBuff = closures.applyBuff,
            vehicleManager = vehicleManager,
            scenes = sceneRegistry,
            claimRegistry = claimRegistry,
            claimManager = claimManager,
        )
    }
}
