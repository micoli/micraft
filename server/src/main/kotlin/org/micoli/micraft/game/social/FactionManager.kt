package org.micoli.micraft.game.social

import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.FactionsSection
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.social.FactionDefinition
import org.micoli.micraft.social.FactionState

class FactionManager(
    private val getSessions: () -> Collection<PlayerSession>,
    private val savePlayer: (PlayerSession) -> Unit,
    private val chatService: ChatService,
    private val channelManager: ChatChannelManager,
    private val i18n: I18nConfig,
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val persistence: WorldPersistence? = null,
) {
    @Volatile private var enabled = false
    @Volatile private var friendlyFire = false
    @Volatile private var changeCooldownMs = 0L
    @Volatile private var defs: List<FactionDefinition> = emptyList()
    private val counts = ConcurrentHashMap<String, Int>()

    fun applyConfig(section: FactionsSection) {
        enabled = section.enabled
        friendlyFire = section.friendlyFire
        changeCooldownMs = section.changeCooldownSeconds * 1000L
        defs = section.list
        counts.clear()
        section.list.forEach { counts[it.id] = 0 }
        persistence?.allPlayerStates()?.forEach { st ->
            st.factionId?.let { if (counts.containsKey(it)) counts.merge(it, 1) { a, b -> a + b } }
        }
        channelManager.let { m -> section.list.forEach { m.registerChannel("faction:${it.id}") } }
    }

    /** After config change: drop affiliations to factions that no longer exist. */
    suspend fun reconcile() {
        val validIds = defs.map { it.id }.toSet()
        getSessions().forEach { s ->
            val fid = s.state.factionId
            if (fid != null && (fid !in validIds || !enabled)) {
                s.state = s.state.copy(factionId = null, factionChangedAtMs = null)
                savePlayer(s)
                chatService.forceUnsubscribe(s, "faction:$fid")
                chatService.syncChannels(s)
                s.send(
                    ServerMessage.Notification(i18n.t(s.state.language, "faction:server:removed")))
                sendSync(s)
            }
        }
        broadcastStates()
    }

    fun isEnabled() = enabled

    fun friendlyFireEnabled() = friendlyFire

    fun definitions(): List<FactionDefinition> = defs

    fun sameFaction(a: PlayerSession, b: PlayerSession): Boolean =
        a.state.factionId != null && a.state.factionId == b.state.factionId

    fun sameFaction(playerId: String, otherPlayerId: String): Boolean {
        val a = getSessions().find { it.id == playerId }?.state?.factionId ?: return false
        val b = getSessions().find { it.id == otherPlayerId }?.state?.factionId ?: return false
        return a == b
    }

    private fun cooldownRemaining(session: PlayerSession): Long {
        val last = session.state.factionChangedAtMs ?: return 0L
        return (last + changeCooldownMs - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    suspend fun setAffiliation(session: PlayerSession, factionId: String?) {
        val lang = session.state.language
        if (!enabled)
            return session.send(
                ServerMessage.SocialDenied("faction", i18n.t(lang, "faction:server:disabled")))
        if (factionId != null && defs.none { it.id == factionId })
            return session.send(
                ServerMessage.SocialDenied("faction", i18n.t(lang, "faction:server:unknown")))
        val remaining = cooldownRemaining(session)
        if (remaining > 0)
            return session.send(
                ServerMessage.SocialDenied(
                    "faction", i18n.t(lang, "faction:server:cooldown", remaining / 1000)))
        val previous = session.state.factionId
        if (previous == factionId) return
        previous?.let {
            counts.merge(it, -1) { a, b -> a + b }
            chatService.forceUnsubscribe(session, "faction:$it")
        }
        factionId?.let {
            counts.merge(it, 1) { a, b -> a + b }
            channelManager.registerChannel("faction:$it")
            chatService.subscribe(session, "faction:$it")
        }
        session.state =
            session.state.copy(
                factionId = factionId, factionChangedAtMs = System.currentTimeMillis())
        savePlayer(session)
        chatService.syncChannels(session)
        sendSync(session)
        broadcastStates()
    }

    private fun states(): List<FactionState> = defs.map { FactionState(it.id, counts[it.id] ?: 0) }

    suspend fun sendSync(session: PlayerSession) {
        session.send(
            ServerMessage.FactionSync(
                enabled = enabled,
                definitions = defs,
                states = states(),
                myFactionId = session.state.factionId,
                changeCooldownRemainingMs = cooldownRemaining(session),
            ))
    }

    private suspend fun broadcastStates() {
        getSessions().forEach { sendSync(it) }
    }
}
