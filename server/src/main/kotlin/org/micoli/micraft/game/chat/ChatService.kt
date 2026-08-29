package org.micoli.micraft.game.chat

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.ChannelSubscription
import org.micoli.micraft.player.hasChannel
import org.micoli.micraft.protocol.ServerMessage

private const val AROUND_RADIUS_SQ = 64f * 64f

class ChatService(
    private val channelManager: ChatChannelManager,
    private val savePlayer: (PlayerSession) -> Unit,
    private val getSessions: () -> Collection<PlayerSession>,
) {
    /**
     * Resolves member player-ids for a `group:<id>` / `guild:<id>` channel. Wired
     * post-construction.
     */
    var groupMembers: (String) -> Set<String> = { emptySet() }
    var guildMembers: (String) -> Set<String> = { emptySet() }

    suspend fun subscribe(session: PlayerSession, channel: String): Boolean {
        if (session.state.subscribedChannels.hasChannel(channel)) return false
        session.state =
            session.state.copy(
                subscribedChannels =
                    session.state.subscribedChannels + ChannelSubscription(channel))
        savePlayer(session)
        return true
    }

    suspend fun unsubscribe(session: PlayerSession, channel: String): Boolean {
        if (channel in ChatChannelManager.PROTECTED) return false
        return forceUnsubscribe(session, channel)
    }

    /**
     * Removes a subscription regardless of PROTECTED status (used when a group/guild dissolves).
     */
    suspend fun forceUnsubscribe(session: PlayerSession, channel: String): Boolean {
        if (!session.state.subscribedChannels.hasChannel(channel)) return false
        session.state =
            session.state.copy(
                subscribedChannels =
                    session.state.subscribedChannels.filterNot { it.name == channel })
        savePlayer(session)
        return true
    }

    suspend fun openDm(sender: PlayerSession, target: PlayerSession) {
        val channel = channelManager.dmChannelName(sender.state.name, target.state.name)
        subscribe(sender, channel)
        subscribe(target, channel)
        syncChannels(sender)
        syncChannels(target)
    }

    suspend fun routeMessage(sender: PlayerSession, channel: String, text: String) {
        val msg = ServerMessage.ChatMessage(channel, sender.state.name, text)
        val all = getSessions()
        when {
            channel == "world" ->
                all.filter { it.state.subscribedChannels.hasChannel("world") }
                    .forEach { it.send(msg) }
            channel == "around" -> {
                val sp = sender.state.pos
                all.filter { it.state.subscribedChannels.hasChannel("around") }
                    .filter { other ->
                        val dx = other.state.pos.x - sp.x
                        val dy = other.state.pos.y - sp.y
                        val dz = other.state.pos.z - sp.z
                        dx * dx + dy * dy + dz * dz <= AROUND_RADIUS_SQ
                    }
                    .forEach { it.send(msg) }
            }
            channel.startsWith("dm:") -> {
                val parts = channel.removePrefix("dm:").split(":")
                val recipients = all.filter { it.state.name in parts }
                recipients.forEach { session ->
                    if (!session.state.subscribedChannels.hasChannel(channel)) {
                        subscribe(session, channel)
                        syncChannels(session)
                    }
                    session.send(msg)
                }
            }
            channel.startsWith("group:") -> {
                val ids = groupMembers(channel.removePrefix("group:"))
                if (sender.id !in ids) return
                all.filter { it.id in ids }.forEach { it.send(msg) }
            }
            channel.startsWith("guild:") -> {
                val ids = guildMembers(channel.removePrefix("guild:"))
                if (sender.id !in ids) return
                all.filter { it.id in ids }.forEach { it.send(msg) }
            }
            channel.startsWith("faction:") -> {
                val factionId = channel.removePrefix("faction:")
                if (sender.state.factionId != factionId) return
                all.filter { it.state.factionId == factionId }.forEach { it.send(msg) }
            }
            else ->
                all.filter { it.state.subscribedChannels.hasChannel(channel) }
                    .forEach { it.send(msg) }
        }
    }

    suspend fun syncChannels(session: PlayerSession) {
        session.send(
            ServerMessage.ChannelsSync(
                subscribedChannels = session.state.subscribedChannels,
                knownChannels = channelManager.listKnownChannels(),
            ))
    }

    suspend fun onPlayerConnect(session: PlayerSession) = syncChannels(session)
}
