package org.micoli.micraft.command

import org.micoli.micraft.I18nConfig
import org.micoli.micraft.auth.AuthProvider
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.combat.StatusEffect
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
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.scene.SceneRegistry
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage

data class CommandContext(
    val world: WorldState,
    val persistence: WorldPersistence?,
    val i18n: I18nConfig,
    val broadcast: suspend (ServerMessage) -> Unit = {},
    val sessions: () -> Collection<PlayerSession> = { emptyList() },
    val kickSession: suspend (String) -> Unit = {},
    val reloadConfig: (suspend (lang: String) -> String)? = null,
    val commands: () -> Collection<CommandHandler> = { emptyList() },
    val savePlayer: (PlayerSession) -> Unit = {},
    val worldItems: WorldItemManager? = null,
    val npcManager: NpcManager? = null,
    val vehicleManager: VehicleManager? = null,
    val getGameTime: () -> Long = { 0L },
    val setGameTime: (Long) -> Unit = {},
    val refetchChunks: (suspend (PlayerSession) -> Unit)? = null,
    val flushWorld: (() -> Unit)? = null,
    val chatService: ChatService? = null,
    val chatChannelManager: ChatChannelManager? = null,
    val weatherManager: WeatherManager? = null,
    val authProvider: AuthProvider? = null,
    val groupsConfig: GroupsConfig? = null,
    val liquidManager: LiquidManager? = null,
    val configRegistry: ConfigRegistry? = null,
    val reloadBlocks: (suspend () -> Unit)? = null,
    val reloadNpcs: (suspend () -> Unit)? = null,
    val reloadRbac: (() -> Unit)? = null,
    val armorRegistry: () -> Map<String, ArmorDefinition> = { emptyMap() },
    val weaponRegistry: () -> Map<String, WeaponDefinition> = { emptyMap() },
    val toolRegistry: () -> Map<String, ToolDefinition> = { emptyMap() },
    val weaponCategories: () -> Map<EquipmentCategory, WeaponCategoryDefinition> = { emptyMap() },
    val toolCategories: () -> Map<EquipmentCategory, ToolCategoryDefinition> = { emptyMap() },
    val tradeManager: TradeManager? = null,
    val clearAccumulators: ((String) -> Unit)? = null,
    val sendStatusUpdate: (suspend (PlayerSession) -> Unit)? = null,
    val namedPoints: () -> Map<String, Vec3> = { emptyMap() },
    val questManager: QuestManager? = null,
    val applyBuff: (suspend (PlayerSession, StatusEffect, Float) -> Unit)? = null, // null in tests
    val scenes: SceneRegistry? = null,
)
