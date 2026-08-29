package org.micoli.micraft.game.social

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.social.GuildBankEntryInfo
import org.micoli.micraft.social.GuildInfoDto
import org.micoli.micraft.social.GuildMemberInfo
import org.micoli.micraft.social.GuildPermission
import org.micoli.micraft.social.GuildRank

@Serializable
data class GuildMember(
    val playerId: String,
    val playerName: String,
    val rank: String,
    val joinedAtMs: Long,
)

@Serializable
data class GuildBankEntry(
    val playerName: String,
    val itemType: ItemType,
    val delta: Int,
    val atMs: Long,
)

@Serializable
data class Guild(
    val id: String,
    val name: String,
    val tag: String,
    val motd: String = "",
    val createdAtMs: Long,
    val ownerId: String,
    val ranks: List<GuildRank>,
    val members: List<GuildMember>,
    val bank: Map<ItemType, Int> = emptyMap(),
    val bankLog: List<GuildBankEntry> = emptyList(),
) {
    val channel: String
        get() = "guild:$id"

    fun member(playerId: String): GuildMember? = members.find { it.playerId == playerId }

    fun rankOf(playerId: String): GuildRank? =
        member(playerId)?.let { m -> ranks.find { it.name == m.rank } }

    fun flagsOf(playerId: String): Set<GuildPermission> = rankOf(playerId)?.flags ?: emptySet()

    fun hasPerm(playerId: String, perm: GuildPermission): Boolean =
        playerId == ownerId || perm in flagsOf(playerId)

    val topRank: GuildRank
        get() = ranks.maxByOrNull { it.order } ?: ranks.first()
}

fun Guild.toDto(forPlayerId: String, onlineIds: Set<String>): GuildInfoDto =
    GuildInfoDto(
        id = id,
        name = name,
        tag = tag,
        motd = motd,
        createdAtMs = createdAtMs,
        ownerId = ownerId,
        ranks = ranks.sortedByDescending { it.order },
        members =
            members.map {
                GuildMemberInfo(
                    playerId = it.playerId,
                    playerName = it.playerName,
                    rank = it.rank,
                    joinedAtMs = it.joinedAtMs,
                    online = it.playerId in onlineIds,
                )
            },
        bank = bank,
        bankLog =
            bankLog.takeLast(org.micoli.micraft.social.SocialConstants.GUILD_BANK_LOG_MAX).map {
                GuildBankEntryInfo(it.playerName, it.itemType.id, it.delta, it.atMs)
            },
        myRank = member(forPlayerId)?.rank ?: "",
        myFlags = flagsOf(forPlayerId),
    )
