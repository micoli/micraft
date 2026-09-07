package org.micoli.micraft.game.social

import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.FactionsSection
import org.micoli.micraft.game.SPAWN_Y
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.social.FactionDefinition
import org.micoli.micraft.social.FactionState
import org.slf4j.LoggerFactory

class FactionManager(
    private val getSessions: () -> Collection<PlayerSession>,
    private val savePlayer: (PlayerSession) -> Unit,
    private val chatService: ChatService,
    private val channelManager: ChatChannelManager,
    private val i18n: I18nConfig,
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val persistence: WorldPersistence? = null,
    private val zoneLevelAt: (Int, Int) -> Int = { _, _ -> 0 },
    private val lowLevelSpawnSlots: (Int, Double) -> List<Pair<Int, Int>> = { _, _ -> emptyList() },
) {
    private val log = LoggerFactory.getLogger(FactionManager::class.java)

    @Volatile private var enabled = false
    @Volatile private var friendlyFire = false
    @Volatile private var changeCooldownMs = 0L
    @Volatile private var defs: List<FactionDefinition> = emptyList()
    @Volatile private var factionSpawns: Map<String, Vec3> = emptyMap()
    private val counts = ConcurrentHashMap<String, Int>()

    fun applyConfig(section: FactionsSection) {
        enabled = section.enabled
        friendlyFire = section.friendlyFire
        changeCooldownMs = section.changeCooldownSeconds * 1000L
        defs = section.list
        factionSpawns = computeSpawns(section)
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

    private fun computeSpawns(section: FactionsSection): Map<String, Vec3> {
        if (section.list.isEmpty()) return emptyMap()
        val autoCount = section.list.count { it.spawnX == null || it.spawnZ == null }
        val autoSlots = ArrayDeque(lowLevelSpawnSlots(autoCount, section.spawnRingRadius))
        return section.list.associate { def ->
            val (x, z) =
                if (def.spawnX != null && def.spawnZ != null) {
                    if (zoneLevelAt(def.spawnX!!, def.spawnZ!!) >= 5)
                        log.warn(
                            "Faction '{}' spawn ({},{}) is in zone level >= 5",
                            def.id,
                            def.spawnX,
                            def.spawnZ)
                    def.spawnX!! to def.spawnZ!!
                } else {
                    autoSlots.removeFirstOrNull() ?: (0 to 0)
                }
            def.id to Vec3(x + 0.5f, SPAWN_Y, z + 0.5f)
        }
    }

    fun spawnFor(factionId: String?): Vec3? = factionId?.let { factionSpawns[it] }

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
        val firstJoinSpawn =
            if (previous == null && factionId != null) factionSpawns[factionId] else null
        session.state =
            session.state.copy(
                factionId = factionId,
                factionChangedAtMs = System.currentTimeMillis(),
                pos = firstJoinSpawn ?: session.state.pos)
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
