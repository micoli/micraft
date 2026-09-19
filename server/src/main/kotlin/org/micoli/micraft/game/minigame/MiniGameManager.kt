package org.micoli.micraft.game.minigame

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.minigame.MiniGameConstants
import org.micoli.micraft.minigame.MiniGameMemberInfo
import org.micoli.micraft.minigame.MiniGameRoomInfo
import org.micoli.micraft.protocol.ServerMessage

class MiniGameMember(val playerId: String, val playerName: String)

class MiniGameRoom(val id: String, var hostId: String, var hostName: String, val gameType: String) {
    val members: MutableList<MiniGameMember> = mutableListOf()
}

/**
 * Ephemeral mini-game rooms: pure network routing between members (create/invite/leave/broadcast),
 * never persisted. Mirrors [org.micoli.micraft.game.social.GroupManager]'s shape minus the chat
 * channel — a mini-game room has no chat of its own. The action payload is opaque JSON, broadcast
 * to the other members verbatim; this manager never parses it, since the game's rules live entirely
 * client-side.
 */
class MiniGameManager(
    private val getSessions: () -> Collection<PlayerSession>,
    private val registry: MiniGameRegistry,
    private val i18n: I18nConfig,
) {
    private val rooms = ConcurrentHashMap<String, MiniGameRoom>()
    private val pendingInvites =
        ConcurrentHashMap<String, Pair<String, Long>>() // targetId -> (roomId, expiresAt)

    fun roomOf(playerId: String): MiniGameRoom? =
        rooms.values.find { r -> r.members.any { it.playerId == playerId } }

    fun pendingRoomIdFor(playerId: String): String? =
        pendingInvites[playerId]?.takeIf { it.second >= System.currentTimeMillis() }?.first

    private fun sessionOf(playerId: String) = getSessions().find { it.id == playerId }

    private fun t(session: PlayerSession, key: String, vararg args: Any) =
        i18n.t(session.state.language, key, *args)

    private suspend fun deny(session: PlayerSession, reason: String) =
        session.send(ServerMessage.SocialDenied("minigame", reason))

    private fun maxPlayersOf(room: MiniGameRoom): Int =
        registry.find(room.gameType)?.maxPlayers ?: MiniGameConstants.DEFAULT_MAX_PLAYERS

    suspend fun create(host: PlayerSession, gameType: String) {
        if (roomOf(host.id) != null) {
            deny(host, t(host, "minigame:server:already_in_room"))
            return
        }
        if (registry.find(gameType) == null) {
            deny(host, t(host, "minigame:server:unknown_game_type", gameType))
            return
        }
        val room = MiniGameRoom(UUID.randomUUID().toString(), host.id, host.state.name, gameType)
        room.members.add(MiniGameMember(host.id, host.state.name))
        rooms[room.id] = room
        pushSync(room)
        host.send(ServerMessage.Notification(t(host, "minigame:server:created")))
    }

    suspend fun invite(host: PlayerSession, roomId: String, targetName: String) {
        val room = rooms[roomId]
        if (room == null) {
            deny(host, t(host, "minigame:server:not_in_room"))
            return
        }
        if (room.hostId != host.id) {
            deny(host, t(host, "minigame:server:not_host"))
            return
        }
        if (room.members.size >= maxPlayersOf(room)) {
            deny(host, t(host, "minigame:server:full"))
            return
        }
        val target = getSessions().find { it.state.name.equals(targetName, ignoreCase = true) }
        if (target == null) {
            deny(host, t(host, "minigame:server:player_not_found", targetName))
            return
        }
        if (roomOf(target.id) != null) {
            deny(host, t(host, "minigame:server:target_in_room"))
            return
        }
        pendingInvites[target.id] =
            room.id to (System.currentTimeMillis() + MiniGameConstants.INVITE_TTL_MS)
        target.send(ServerMessage.MiniGameInviteReceived(room.id, room.gameType, host.state.name))
        host.send(ServerMessage.Notification(t(host, "minigame:server:invited", target.state.name)))
    }

    suspend fun respondInvite(target: PlayerSession, roomId: String, accept: Boolean) {
        val pending = pendingInvites.remove(target.id)
        if (pending == null ||
            pending.first != roomId ||
            pending.second < System.currentTimeMillis()) {
            deny(target, t(target, "minigame:server:invite_expired"))
            return
        }
        if (!accept) return
        val room =
            rooms[roomId]
                ?: run {
                    deny(target, t(target, "minigame:server:invite_expired"))
                    return
                }
        if (roomOf(target.id) != null) {
            deny(target, t(target, "minigame:server:already_in_room"))
            return
        }
        if (room.members.size >= maxPlayersOf(room)) {
            deny(target, t(target, "minigame:server:full"))
            return
        }
        room.members.add(MiniGameMember(target.id, target.state.name))
        target.send(ServerMessage.Notification(t(target, "minigame:server:joined", room.hostName)))
        pushSync(room)
    }

    /** A player leaving mid-game stops it for everyone — never partial continuation/re-hosting. */
    suspend fun leave(session: PlayerSession, roomId: String) {
        val room = rooms[roomId] ?: return
        if (room.members.none { it.playerId == session.id }) return
        dissolve(room)
    }

    /** Broadcasts [payload] to every other member of the room — never parsed, never validated. */
    suspend fun broadcastAction(sender: PlayerSession, roomId: String, payload: String) {
        val room = rooms[roomId] ?: return
        if (room.members.none { it.playerId == sender.id }) return
        val msg = ServerMessage.MiniGameAction(roomId, sender.id, payload)
        room.members
            .filter { it.playerId != sender.id }
            .forEach { m -> sessionOf(m.playerId)?.send(msg) }
    }

    /** A disconnect is a leave too: it stops the match for every remaining member. */
    suspend fun onDisconnect(session: PlayerSession) {
        val room = roomOf(session.id) ?: return
        dissolve(room)
    }

    private suspend fun dissolve(room: MiniGameRoom) {
        rooms.remove(room.id)
        room.members.forEach { m ->
            sessionOf(m.playerId)?.send(ServerMessage.MiniGameRoomSync(null))
        }
    }

    suspend fun sendSync(session: PlayerSession) {
        session.send(ServerMessage.MiniGameRoomSync(roomOf(session.id)?.let { toInfo(it) }))
    }

    private suspend fun pushSync(room: MiniGameRoom) {
        val info = toInfo(room)
        room.members.forEach { m ->
            sessionOf(m.playerId)?.send(ServerMessage.MiniGameRoomSync(info))
        }
    }

    private fun toInfo(room: MiniGameRoom): MiniGameRoomInfo {
        val onlineIds = getSessions().map { it.id }.toSet()
        return MiniGameRoomInfo(
            id = room.id,
            hostId = room.hostId,
            hostName = room.hostName,
            gameType = room.gameType,
            members =
                room.members.map {
                    MiniGameMemberInfo(it.playerId, it.playerName, it.playerId in onlineIds)
                },
        )
    }
}
