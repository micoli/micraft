package org.micoli.micraft.world

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(WorldItemManager::class.java)
private const val COLLECTION_RADIUS_SQ = 1.5f * 1.5f

class WorldItemManager(
    private val dropConfig: DropConfig,
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val savePlayer: (PlayerSession) -> Unit = {},
    private val i18n: I18nConfig? = null,
) {
    private val items = ConcurrentHashMap<String, WorldItem>()

    suspend fun spawnDrops(blockPos: BlockPos, blockType: BlockType): List<WorldItem> {
        val drops = dropConfig.rollDrops(blockType)
        if (drops.isEmpty()) return emptyList()
        val center = Vec3(blockPos.x + 0.5f, blockPos.y + 0.5f, blockPos.z + 0.5f)
        val spawned =
            drops.map { (itemType, count) ->
                WorldItem(UUID.randomUUID().toString(), center, itemType, count).also {
                    items[it.id] = it
                    log.info(
                        "Drop spawned: {}x {} at ({},{},{})",
                        count,
                        itemType,
                        blockPos.x,
                        blockPos.y,
                        blockPos.z)
                }
            }
        broadcast(ServerMessage.ItemsSpawned(spawned))
        return spawned
    }

    fun itemCount(): Int = items.size

    fun hasItem(id: String): Boolean = items.containsKey(id)

    suspend fun despawnItem(id: String) {
        if (items.remove(id) != null) {
            broadcast(ServerMessage.ItemDespawned(id))
        }
    }

    suspend fun tickCollection(sessions: Collection<PlayerSession>) {
        for (session in sessions) {
            val playerPos = session.state.pos
            val toCollect =
                items.values.filter { item ->
                    val dx = item.pos.x - playerPos.x
                    val dy = item.pos.y - (playerPos.y + 0.9f)
                    val dz = item.pos.z - playerPos.z
                    dx * dx + dy * dy + dz * dz <= COLLECTION_RADIUS_SQ
                }
            for (item in toCollect) {
                if (items.remove(item.id) != null) {
                    session.inventory.merge(item.type, item.count, Int::plus)
                    val total = session.inventory[item.type] ?: 0
                    log.info(
                        "Item collected: {}x {} by {} (total: {})",
                        item.count,
                        item.type,
                        session.state.name,
                        total)
                    broadcast(ServerMessage.ItemDespawned(item.id))
                    session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
                    savePlayer(session)
                    val label = item.type.id.lowercase().replaceFirstChar { it.uppercase() }
                    val msg =
                        i18n?.t(
                            session.state.language,
                            "inventory:server:item_picked_up",
                            item.count,
                            label) ?: "+${item.count} $label"
                    session.send(ServerMessage.Notification(msg, "game"))
                }
            }
        }
    }
}
