package org.micoli.micraft.game.social

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.addItems
import org.micoli.micraft.game.session.removeItems
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.social.GuildPermission
import org.micoli.micraft.social.GuildRank
import org.micoli.micraft.social.SocialConstants
import org.micoli.micraft.social.defaultGuildRanks

class GuildManager(
    private val registry: GuildRegistry,
    private val getSessions: () -> Collection<PlayerSession>,
    private val savePlayer: (PlayerSession) -> Unit,
    private val chatService: ChatService,
    private val channelManager: ChatChannelManager,
    private val i18n: I18nConfig,
    /** Delivers leftover bank items to a (possibly offline) player, e.g. via system mail. */
    private val returnBankItems: suspend (playerName: String, items: Map<ItemType, Int>) -> Unit =
        { _, _ ->
        },
) {
    private val pendingInvites =
        ConcurrentHashMap<String, Pair<String, Long>>() // targetId -> (guildId, expiresAt)

    init {
        registry.all().forEach { channelManager.registerChannel(it.channel) }
    }

    private fun onlineIds() = getSessions().map { it.id }.toSet()

    private fun sessionOf(id: String) = getSessions().find { it.id == id }

    private fun t(session: PlayerSession, key: String, vararg args: Any) =
        i18n.t(session.state.language, key, *args)

    private suspend fun deny(session: PlayerSession, reason: String) =
        session.send(ServerMessage.SocialDenied("guild", reason))

    fun memberIds(guildId: String) = registry.memberIds(guildId)

    fun pendingGuildIdFor(playerId: String): String? =
        pendingInvites[playerId]?.takeIf { it.second >= System.currentTimeMillis() }?.first

    suspend fun sendSync(session: PlayerSession) {
        val guild = registry.guildOf(session.id)
        session.send(ServerMessage.GuildSync(guild?.toDto(session.id, onlineIds())))
    }

    private suspend fun pushSync(guild: Guild) {
        val online = onlineIds()
        guild.members.forEach { m ->
            sessionOf(m.playerId)?.send(ServerMessage.GuildSync(guild.toDto(m.playerId, online)))
        }
    }

    suspend fun create(founder: PlayerSession, name: String, tag: String) {
        val lang = founder.state.language
        if (founder.state.guildId != null)
            return deny(founder, t(founder, "guild:server:already_in_guild"))
        val cleanName = name.trim()
        val cleanTag = tag.trim()
        if (cleanName.length !in 3..SocialConstants.GUILD_NAME_MAX_LEN)
            return deny(founder, t(founder, "guild:server:bad_name"))
        if (cleanTag.length !in 1..SocialConstants.GUILD_TAG_MAX_LEN)
            return deny(founder, t(founder, "guild:server:bad_tag"))
        if (registry.byName(cleanName) != null)
            return deny(founder, t(founder, "guild:server:name_taken"))
        if (registry.byTag(cleanTag) != null)
            return deny(founder, t(founder, "guild:server:tag_taken"))

        val ranks = defaultGuildRanks()
        val topRank = ranks.maxByOrNull { it.order }!!
        val now = System.currentTimeMillis()
        val guild =
            Guild(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                tag = cleanTag,
                createdAtMs = now,
                ownerId = founder.id,
                ranks = ranks,
                members = listOf(GuildMember(founder.id, founder.state.name, topRank.name, now)),
            )
        registry.add(guild)
        channelManager.registerChannel(guild.channel)
        founder.state =
            founder.state.copy(guildId = guild.id, guildRank = topRank.name, guildTag = guild.tag)
        savePlayer(founder)
        chatService.subscribe(founder, guild.channel)
        chatService.syncChannels(founder)
        pushSync(guild)
        founder.send(ServerMessage.Notification(t(founder, "guild:server:created", cleanName)))
    }

    suspend fun invite(inviter: PlayerSession, targetName: String) {
        val guild =
            registry.guildOf(inviter.id)
                ?: return deny(inviter, t(inviter, "guild:server:not_in_guild"))
        if (!guild.hasPerm(inviter.id, GuildPermission.INVITE))
            return deny(inviter, t(inviter, "guild:server:no_perm"))
        val target =
            getSessions().find { it.state.name.equals(targetName, ignoreCase = true) }
                ?: return deny(inviter, t(inviter, "guild:server:player_not_found", targetName))
        if (target.state.guildId != null)
            return deny(inviter, t(inviter, "guild:server:target_in_guild"))
        pendingInvites[target.id] =
            guild.id to (System.currentTimeMillis() + SocialConstants.GUILD_INVITE_TTL_MS)
        target.send(ServerMessage.GuildInviteReceived(guild.id, guild.name, inviter.state.name))
        inviter.send(
            ServerMessage.Notification(t(inviter, "guild:server:invited", target.state.name)))
    }

    suspend fun respondInvite(target: PlayerSession, guildId: String, accept: Boolean) {
        val pending = pendingInvites.remove(target.id)
        if (pending == null ||
            pending.first != guildId ||
            pending.second < System.currentTimeMillis())
            return deny(target, t(target, "guild:server:invite_expired"))
        if (!accept) return
        if (target.state.guildId != null)
            return deny(target, t(target, "guild:server:already_in_guild"))
        val guild =
            registry.get(guildId) ?: return deny(target, t(target, "guild:server:invite_expired"))
        val lowestRank = guild.ranks.minByOrNull { it.order }!!
        val updated =
            guild.copy(
                members =
                    guild.members +
                        GuildMember(
                            target.id,
                            target.state.name,
                            lowestRank.name,
                            System.currentTimeMillis()))
        registry.update(updated)
        target.state =
            target.state.copy(guildId = guild.id, guildRank = lowestRank.name, guildTag = guild.tag)
        savePlayer(target)
        chatService.subscribe(target, guild.channel)
        chatService.syncChannels(target)
        pushSync(updated)
    }

    suspend fun leave(session: PlayerSession) {
        val guild = registry.guildOf(session.id) ?: return
        if (guild.ownerId == session.id && guild.members.size > 1)
            return deny(session, t(session, "guild:server:owner_must_transfer"))
        removeMember(guild, session.id)
    }

    suspend fun kick(actor: PlayerSession, targetId: String) {
        val guild =
            registry.guildOf(actor.id) ?: return deny(actor, t(actor, "guild:server:not_in_guild"))
        if (!guild.hasPerm(actor.id, GuildPermission.KICK))
            return deny(actor, t(actor, "guild:server:no_perm"))
        if (targetId == guild.ownerId)
            return deny(actor, t(actor, "guild:server:cannot_kick_owner"))
        if (guild.member(targetId) == null) return
        removeMember(guild, targetId)
    }

    private suspend fun removeMember(guild: Guild, playerId: String) {
        if (guild.members.size == 1 && guild.ownerId == playerId) {
            dissolve(guild)
            return
        }
        val updated = guild.copy(members = guild.members.filterNot { it.playerId == playerId })
        registry.update(updated)
        sessionOf(playerId)?.let { s ->
            s.state = s.state.copy(guildId = null, guildRank = null, guildTag = null)
            savePlayer(s)
            chatService.forceUnsubscribe(s, guild.channel)
            chatService.syncChannels(s)
            s.send(ServerMessage.GuildSync(null))
        }
        pushSync(updated)
    }

    suspend fun setMotd(actor: PlayerSession, text: String) {
        val guild = registry.guildOf(actor.id) ?: return
        if (!guild.hasPerm(actor.id, GuildPermission.EDIT_MOTD))
            return deny(actor, t(actor, "guild:server:no_perm"))
        val updated = guild.copy(motd = text.take(500))
        registry.update(updated)
        pushSync(updated)
    }

    suspend fun setRank(actor: PlayerSession, targetId: String, rankName: String) {
        val guild = registry.guildOf(actor.id) ?: return
        if (!guild.hasPerm(actor.id, GuildPermission.MANAGE_RANKS))
            return deny(actor, t(actor, "guild:server:no_perm"))
        val rank =
            guild.ranks.find { it.name == rankName }
                ?: return deny(actor, t(actor, "guild:server:unknown_rank"))
        val member = guild.member(targetId) ?: return
        if (targetId == guild.ownerId)
            return deny(actor, t(actor, "guild:server:cannot_rank_owner"))
        val actorRank = guild.rankOf(actor.id)?.order ?: 0
        if (actor.id != guild.ownerId && rank.order >= actorRank)
            return deny(actor, t(actor, "guild:server:rank_too_high"))
        val updated =
            guild.copy(
                members =
                    guild.members.map {
                        if (it.playerId == targetId) it.copy(rank = rankName) else it
                    })
        registry.update(updated)
        sessionOf(targetId)?.let {
            it.state = it.state.copy(guildRank = rankName)
            savePlayer(it)
        }
        pushSync(updated)
    }

    suspend fun upsertRank(actor: PlayerSession, rank: GuildRank) {
        val guild = registry.guildOf(actor.id) ?: return
        if (!guild.hasPerm(actor.id, GuildPermission.MANAGE_RANKS))
            return deny(actor, t(actor, "guild:server:no_perm"))
        val ranks = guild.ranks.filterNot { it.name == rank.name } + rank
        registry.update(guild.copy(ranks = ranks))
        pushSync(registry.get(guild.id)!!)
    }

    suspend fun deleteRank(actor: PlayerSession, rankName: String) {
        val guild = registry.guildOf(actor.id) ?: return
        if (!guild.hasPerm(actor.id, GuildPermission.MANAGE_RANKS))
            return deny(actor, t(actor, "guild:server:no_perm"))
        if (guild.members.any { it.rank == rankName })
            return deny(actor, t(actor, "guild:server:rank_in_use"))
        if (guild.ranks.size <= 1) return
        registry.update(guild.copy(ranks = guild.ranks.filterNot { it.name == rankName }))
        pushSync(registry.get(guild.id)!!)
    }

    suspend fun transferOwner(actor: PlayerSession, targetId: String) {
        val guild = registry.guildOf(actor.id) ?: return
        if (actor.id != guild.ownerId) return deny(actor, t(actor, "guild:server:not_owner"))
        val target = guild.member(targetId) ?: return
        val topRank = guild.topRank.name
        val secondRank = guild.ranks.sortedByDescending { it.order }.getOrNull(1)?.name ?: topRank
        val updated =
            guild.copy(
                ownerId = targetId,
                members =
                    guild.members.map {
                        when (it.playerId) {
                            targetId -> it.copy(rank = topRank)
                            actor.id -> it.copy(rank = secondRank)
                            else -> it
                        }
                    })
        registry.update(updated)
        sessionOf(actor.id)?.let {
            it.state = it.state.copy(guildRank = secondRank)
            savePlayer(it)
        }
        sessionOf(targetId)?.let {
            it.state = it.state.copy(guildRank = topRank)
            savePlayer(it)
        }
        pushSync(updated)
        actor.send(
            ServerMessage.Notification(
                t(actor, "guild:server:ownership_transferred", target.playerName)))
    }

    suspend fun disband(actor: PlayerSession) {
        val guild = registry.guildOf(actor.id) ?: return
        if (actor.id != guild.ownerId) return deny(actor, t(actor, "guild:server:not_owner"))
        dissolve(guild)
    }

    private suspend fun dissolve(guild: Guild) {
        registry.remove(guild.id)
        channelManager.unregisterChannel(guild.channel)
        if (guild.bank.isNotEmpty()) {
            val ownerName = guild.members.find { it.playerId == guild.ownerId }?.playerName
            if (ownerName != null) returnBankItems(ownerName, guild.bank)
        }
        guild.members.forEach { m ->
            sessionOf(m.playerId)?.let { s ->
                s.state = s.state.copy(guildId = null, guildRank = null, guildTag = null)
                savePlayer(s)
                chatService.forceUnsubscribe(s, guild.channel)
                chatService.syncChannels(s)
                s.send(ServerMessage.GuildSync(null))
                s.send(ServerMessage.Notification(t(s, "guild:server:disbanded", guild.name)))
            }
        }
    }

    suspend fun bankDeposit(session: PlayerSession, itemType: ItemType, count: Int) {
        if (count <= 0) return
        val guild =
            registry.guildOf(session.id)
                ?: return deny(session, t(session, "guild:server:not_in_guild"))
        if (!guild.hasPerm(session.id, GuildPermission.BANK_DEPOSIT))
            return deny(session, t(session, "guild:server:no_perm"))
        if (!session.removeItems(mapOf(itemType to count)))
            return deny(session, t(session, "guild:server:not_enough_items"))
        val newBank = guild.bank.toMutableMap()
        newBank.merge(itemType, count) { a, b -> a + b }
        val log =
            (guild.bankLog +
                    GuildBankEntry(session.state.name, itemType, count, System.currentTimeMillis()))
                .takeLast(SocialConstants.GUILD_BANK_LOG_MAX)
        registry.update(guild.copy(bank = newBank, bankLog = log))
        pushSync(registry.get(guild.id)!!)
    }

    suspend fun bankWithdraw(session: PlayerSession, itemType: ItemType, count: Int) {
        if (count <= 0) return
        val guild =
            registry.guildOf(session.id)
                ?: return deny(session, t(session, "guild:server:not_in_guild"))
        if (!guild.hasPerm(session.id, GuildPermission.BANK_WITHDRAW))
            return deny(session, t(session, "guild:server:no_perm"))
        if ((guild.bank[itemType] ?: 0) < count)
            return deny(session, t(session, "guild:server:bank_short"))
        val newBank = guild.bank.toMutableMap()
        val remaining = (newBank[itemType] ?: 0) - count
        if (remaining <= 0) newBank.remove(itemType) else newBank[itemType] = remaining
        val log =
            (guild.bankLog +
                    GuildBankEntry(
                        session.state.name, itemType, -count, System.currentTimeMillis()))
                .takeLast(SocialConstants.GUILD_BANK_LOG_MAX)
        registry.update(guild.copy(bank = newBank, bankLog = log))
        session.addItems(mapOf(itemType to count))
        pushSync(registry.get(guild.id)!!)
    }
}
