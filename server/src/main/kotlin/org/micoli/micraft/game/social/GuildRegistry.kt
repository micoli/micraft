package org.micoli.micraft.game.social

import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.game.world.WorldPersistence

class GuildRegistry(private val persistence: WorldPersistence?) {
    private val guilds = ConcurrentHashMap<String, Guild>()

    init {
        persistence?.loadGuilds()?.forEach { guilds[it.id] = it }
    }

    fun all(): List<Guild> = guilds.values.sortedBy { it.createdAtMs }

    fun get(id: String): Guild? = guilds[id]

    fun byName(name: String): Guild? =
        guilds.values.find { it.name.equals(name, ignoreCase = true) }

    fun byTag(tag: String): Guild? = guilds.values.find { it.tag.equals(tag, ignoreCase = true) }

    fun guildOf(playerId: String): Guild? = guilds.values.find { it.member(playerId) != null }

    fun add(guild: Guild) {
        guilds[guild.id] = guild
        persist()
    }

    fun update(guild: Guild) {
        guilds[guild.id] = guild
        persist()
    }

    fun remove(id: String) {
        guilds.remove(id)
        persist()
    }

    fun memberIds(guildId: String): Set<String> =
        guilds[guildId]?.members?.map { it.playerId }?.toSet() ?: emptySet()

    private fun persist() {
        persistence?.saveGuilds(all())
    }
}
