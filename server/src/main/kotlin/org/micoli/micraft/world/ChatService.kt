package org.micoli.micraft.world

import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import kotlin.math.sqrt

private const val AROUND_RADIUS_SQ = 64f * 64f

class ChatService(
    private val channelManager: ChatChannelManager,
    private val savePlayer: (PlayerSession) -> Unit,
    private val getSessions: () -> Collection<PlayerSession>,
) {
    suspend fun subscribe(session: PlayerSession, channel: String): Boolean {
        if (channel in session.state.subscribedChannels) return false
        session.state = session.state.copy(subscribedChannels = session.state.subscribedChannels + channel)
        savePlayer(session)
        return true
    }

    suspend fun unsubscribe(session: PlayerSession, channel: String): Boolean {
        if (channel in ChatChannelManager.PROTECTED) return false
        if (channel !in session.state.subscribedChannels) return false
        session.state = session.state.copy(subscribedChannels = session.state.subscribedChannels - channel)
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
            channel == "world" -> all.filter { "world" in it.state.subscribedChannels }.forEach { it.send(msg) }
            channel == "around" -> {
                val sp = sender.state.pos
                all.filter { "around" in it.state.subscribedChannels }
                    .filter { other ->
                        val dx = other.state.pos.x - sp.x
                        val dy = other.state.pos.y - sp.y
                        val dz = other.state.pos.z - sp.z
                        dx * dx + dy * dy + dz * dz <= AROUND_RADIUS_SQ
                    }
                    .forEach { it.send(msg) }
            }
            else -> all.filter { channel in it.state.subscribedChannels }.forEach { it.send(msg) }
        }
    }

    suspend fun syncChannels(session: PlayerSession) {
        session.send(
            ServerMessage.ChannelsSync(
                subscribedChannels = session.state.subscribedChannels,
                knownChannels = channelManager.listKnownChannels(),
            )
        )
    }

    suspend fun onPlayerConnect(session: PlayerSession) = syncChannels(session)
}
