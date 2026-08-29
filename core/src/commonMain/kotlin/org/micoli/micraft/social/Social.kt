@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.micoli.micraft.social

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.ItemType

object SocialConstants {
    const val GROUP_MAX_SIZE = 5
    const val GUILD_BANK_LOG_MAX = 100
    const val GUILD_TAG_MAX_LEN = 5
    const val GUILD_NAME_MAX_LEN = 32
    const val GROUP_INVITE_TTL_MS = 60_000L
    const val GUILD_INVITE_TTL_MS = 120_000L
}

enum class GuildPermission {
    INVITE,
    KICK,
    MANAGE_RANKS,
    EDIT_MOTD,
    BANK_DEPOSIT,
    BANK_WITHDRAW,
    DISBAND,
    EDIT_INFO,
}

@Serializable
data class GuildRank(
    val name: String,
    val order: Int,
    @EncodeDefault(ALWAYS) val flags: Set<GuildPermission> = emptySet(),
)

/**
 * Ranks assigned to a guild at creation time. Founder gets the highest ([defaultRanks].first()).
 */
fun defaultGuildRanks(): List<GuildRank> =
    listOf(
        GuildRank("Master", 100, GuildPermission.entries.toSet()),
        GuildRank(
            "Officer",
            50,
            setOf(
                GuildPermission.INVITE,
                GuildPermission.KICK,
                GuildPermission.EDIT_MOTD,
                GuildPermission.BANK_DEPOSIT,
                GuildPermission.BANK_WITHDRAW,
            ),
        ),
        GuildRank("Member", 10, setOf(GuildPermission.BANK_DEPOSIT)),
        GuildRank("Recruit", 0, emptySet()),
    )

@Serializable
data class GuildMemberInfo(
    val playerId: String,
    val playerName: String,
    val rank: String,
    val joinedAtMs: Long,
    @EncodeDefault(ALWAYS) val online: Boolean = false,
)

@Serializable
data class GuildBankEntryInfo(
    val playerName: String,
    val itemId: String,
    val delta: Int,
    val atMs: Long,
)

@Serializable
data class GuildInfoDto(
    val id: String,
    val name: String,
    val tag: String,
    val motd: String,
    val createdAtMs: Long,
    val ownerId: String,
    val ranks: List<GuildRank>,
    val members: List<GuildMemberInfo>,
    @EncodeDefault(ALWAYS) val bank: Map<ItemType, Int> = emptyMap(),
    @EncodeDefault(ALWAYS) val bankLog: List<GuildBankEntryInfo> = emptyList(),
    val myRank: String,
    @EncodeDefault(ALWAYS) val myFlags: Set<GuildPermission> = emptySet(),
)

@Serializable
data class GroupMemberInfo(
    val playerId: String,
    val playerName: String,
    @EncodeDefault(ALWAYS) val online: Boolean = false,
)

@Serializable
data class GroupInfo(
    val id: String,
    val leaderId: String,
    val leaderName: String,
    val members: List<GroupMemberInfo>,
)

@Serializable
data class FactionDefinition(
    val id: String,
    val name: String,
    @EncodeDefault(ALWAYS) val color: String = "#888888",
    @EncodeDefault(ALWAYS) val description: String = "",
)

@Serializable data class FactionState(val id: String, val memberCount: Int)
