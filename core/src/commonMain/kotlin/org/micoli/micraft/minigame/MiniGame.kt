package org.micoli.micraft.minigame

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.Serializable

object MiniGameConstants {
    const val INVITE_TTL_MS = 60_000L
    const val DEFAULT_MAX_PLAYERS = 8
}

@Serializable
data class MiniGameMemberInfo(
    val playerId: String,
    val playerName: String,
    @EncodeDefault(ALWAYS) val online: Boolean = false,
)

@Serializable
data class MiniGameRoomInfo(
    val id: String,
    val hostId: String,
    val hostName: String,
    val gameType: String,
    val members: List<MiniGameMemberInfo>,
)
