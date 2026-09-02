package org.micoli.micraft.game.social

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.social.GroupInfo
import org.micoli.micraft.social.GroupMemberInfo
import org.micoli.micraft.social.SocialConstants

class GroupMember(val playerId: String, val playerName: String)

class Group(val id: String, var leaderId: String, var leaderName: String) {
    val members: MutableList<GroupMember> = mutableListOf()
    val channel: String
        get() = "group:$id"
}

/**
 * Ephemeral player parties (max [SocialConstants.GROUP_MAX_SIZE]). Never persisted: a group is
 * disbanded as soon as none of its members are online.
 */
class GroupManager(
    private val getSessions: () -> Collection<PlayerSession>,
    private val chatService: ChatService,
    private val channelManager: ChatChannelManager,
    private val i18n: I18nConfig,
) {
    private val groups = ConcurrentHashMap<String, Group>()
    private val pendingInvites =
        ConcurrentHashMap<String, Pair<String, Long>>() // targetId -> (groupId, expiresAt)

    fun groupOf(playerId: String): Group? =
        groups.values.find { g -> g.members.any { it.playerId == playerId } }

    fun pendingGroupIdFor(playerId: String): String? =
        pendingInvites[playerId]?.takeIf { it.second >= System.currentTimeMillis() }?.first

    private fun sessionOf(playerId: String) = getSessions().find { it.id == playerId }

    private fun t(session: PlayerSession, key: String, vararg args: Any) =
        i18n.t(session.state.language, key, *args)

    private suspend fun deny(session: PlayerSession, reason: String) =
        session.send(ServerMessage.SocialDenied("group", reason))

    suspend fun create(leader: PlayerSession) {
        if (groupOf(leader.id) != null) {
            deny(leader, t(leader, "group:server:already_in_group"))
            return
        }
        val group = Group(UUID.randomUUID().toString(), leader.id, leader.state.name)
        group.members.add(GroupMember(leader.id, leader.state.name))
        groups[group.id] = group
        channelManager.registerChannel(group.channel)
        chatService.subscribe(leader, group.channel)
        chatService.syncChannels(leader)
        pushSync(group)
        leader.send(ServerMessage.Notification(t(leader, "group:server:created")))
    }

    suspend fun invite(inviter: PlayerSession, targetName: String) {
        val group = groupOf(inviter.id)
        if (group == null) {
            deny(inviter, t(inviter, "group:server:not_in_group"))
            return
        }
        if (group.leaderId != inviter.id) {
            deny(inviter, t(inviter, "group:server:not_leader"))
            return
        }
        if (group.members.size >= SocialConstants.GROUP_MAX_SIZE) {
            deny(inviter, t(inviter, "group:server:full"))
            return
        }
        val target = getSessions().find { it.state.name.equals(targetName, ignoreCase = true) }
        if (target == null) {
            deny(inviter, t(inviter, "group:server:player_not_found", targetName))
            return
        }
        if (groupOf(target.id) != null) {
            deny(inviter, t(inviter, "group:server:target_in_group"))
            return
        }
        pendingInvites[target.id] =
            group.id to (System.currentTimeMillis() + SocialConstants.GROUP_INVITE_TTL_MS)
        target.send(ServerMessage.GroupInviteReceived(group.id, inviter.state.name))
        inviter.send(
            ServerMessage.Notification(t(inviter, "group:server:invited", target.state.name)))
    }

    suspend fun respondInvite(target: PlayerSession, groupId: String, accept: Boolean) {
        val pending = pendingInvites.remove(target.id)
        if (pending == null ||
            pending.first != groupId ||
            pending.second < System.currentTimeMillis()) {
            deny(target, t(target, "group:server:invite_expired"))
            return
        }
        if (!accept) return
        val group =
            groups[groupId]
                ?: run {
                    deny(target, t(target, "group:server:invite_expired"))
                    return
                }
        if (groupOf(target.id) != null) {
            deny(target, t(target, "group:server:already_in_group"))
            return
        }
        if (group.members.size >= SocialConstants.GROUP_MAX_SIZE) {
            deny(target, t(target, "group:server:full"))
            return
        }
        group.members.add(GroupMember(target.id, target.state.name))
        channelManager.registerChannel(group.channel)
        chatService.subscribe(target, group.channel)
        chatService.syncChannels(target)
        target.send(ServerMessage.Notification(t(target, "group:server:joined", group.leaderName)))
        pushSync(group)
    }

    suspend fun leave(session: PlayerSession) {
        val group = groupOf(session.id) ?: return
        removeMember(group, session.id)
    }

    suspend fun kick(leader: PlayerSession, targetId: String) {
        val group = groupOf(leader.id) ?: return
        if (group.leaderId != leader.id) {
            deny(leader, t(leader, "group:server:not_leader"))
            return
        }
        if (targetId == leader.id) return
        removeMember(group, targetId)
    }

    suspend fun transfer(leader: PlayerSession, targetId: String) {
        val group = groupOf(leader.id) ?: return
        if (group.leaderId != leader.id) {
            deny(leader, t(leader, "group:server:not_leader"))
            return
        }
        val member = group.members.find { it.playerId == targetId } ?: return
        group.leaderId = member.playerId
        group.leaderName = member.playerName
        pushSync(group)
    }

    suspend fun disband(leader: PlayerSession) {
        val group = groupOf(leader.id) ?: return
        if (group.leaderId != leader.id) {
            deny(leader, t(leader, "group:server:not_leader"))
            return
        }
        dissolve(group)
    }

    /** Called on every disconnect: a group with no online member left is dissolved. */
    suspend fun onDisconnect(session: PlayerSession) {
        val group = groupOf(session.id) ?: return
        val onlineIds = getSessions().map { it.id }.toSet() - session.id
        if (group.members.none { it.playerId in onlineIds }) {
            groups.remove(group.id)
            channelManager.unregisterChannel(group.channel)
        }
    }

    private suspend fun removeMember(group: Group, playerId: String) {
        group.members.removeAll { it.playerId == playerId }
        sessionOf(playerId)?.let { s ->
            chatService.forceUnsubscribe(s, group.channel)
            chatService.syncChannels(s)
            s.send(ServerMessage.GroupSync(null))
        }
        if (group.members.isEmpty()) {
            groups.remove(group.id)
            channelManager.unregisterChannel(group.channel)
            return
        }
        if (group.leaderId == playerId) {
            val next = group.members.first()
            group.leaderId = next.playerId
            group.leaderName = next.playerName
        }
        pushSync(group)
    }

    private suspend fun dissolve(group: Group) {
        groups.remove(group.id)
        channelManager.unregisterChannel(group.channel)
        group.members.forEach { m ->
            sessionOf(m.playerId)?.let { s ->
                chatService.forceUnsubscribe(s, group.channel)
                chatService.syncChannels(s)
                s.send(ServerMessage.GroupSync(null))
            }
        }
    }

    suspend fun sendSync(session: PlayerSession) {
        val group = groupOf(session.id)
        session.send(ServerMessage.GroupSync(group?.let { toInfo(it) }))
    }

    private suspend fun pushSync(group: Group) {
        val info = toInfo(group)
        group.members.forEach { m -> sessionOf(m.playerId)?.send(ServerMessage.GroupSync(info)) }
    }

    private fun toInfo(group: Group): GroupInfo {
        val onlineIds = getSessions().map { it.id }.toSet()
        return GroupInfo(
            id = group.id,
            leaderId = group.leaderId,
            leaderName = group.leaderName,
            members =
                group.members.map {
                    GroupMemberInfo(it.playerId, it.playerName, it.playerId in onlineIds)
                },
        )
    }

    fun memberIds(groupId: String): Set<String> =
        groups[groupId]?.members?.map { it.playerId }?.toSet() ?: emptySet()
}
