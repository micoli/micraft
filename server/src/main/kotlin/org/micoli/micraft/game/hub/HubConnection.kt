package org.micoli.micraft.game.hub

import io.ktor.websocket.*
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.auth.NoAuthAccountStore
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.di.PlayerPersister
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.GameWorld
import org.micoli.micraft.game.world.GameWorldRegistry
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.AuctionFilter
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ClientMessageCodec
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(HubConnection::class.java)

// Channels routed by ChatService that assume a live, current position (ChatService.kt's "around"
// case reads sender.state.pos) — meaningless for a session whose pos is whatever was last saved.
// Restricting hub chat to these avoids surprising radius behavior against a stale position.
private val HUB_CHAT_CHANNELS =
    setOf("world", "system", "game", "combat").let { builtins ->
        { channel: String ->
            channel in builtins ||
                channel.startsWith("dm:") ||
                channel.startsWith("group:") ||
                channel.startsWith("guild:") ||
                channel.startsWith("faction:")
        }
    }

/**
 * Lifecycle of one `/hub` WebSocket: authenticates like
 * [org.micoli.micraft.game.GameLoop.onConnect] but never joins the world/tick — it either attaches
 * as an extra socket onto an already-connected player's [PlayerSession] (see
 * [PlayerSession.attachExtra]) or, for an offline player, hydrates a lightweight "companion"
 * session from persistence (see [org.micoli.micraft.di.SessionRegistry]).
 *
 * Reuses the game protocol's binary codec as-is ([ClientMessageCodec]/[ServerMessageCodec]) — see
 * the "Encodage des messages" section of the web-companion plan for why no separate wire format was
 * introduced.
 */
class HubConnection(
    private val gameWorldRegistry: GameWorldRegistry,
    private val tokenStore: TokenStore?,
    private val noAuthAccountStore: NoAuthAccountStore?,
    private val i18n: I18nConfig,
) {
    suspend fun handle(
        socket: DefaultWebSocketSession,
        lang: String?,
        gameSessionId: String? = null
    ) {
        // Outside MICRAFT_E2E, or without an id, resolve() always returns defaultWorld — matches
        // GameLoop.onConnect's own resolution for `/game` (worldFor(gameSessionId)), so a hub tab
        // opened with the same `?gameSession=` as a browser E2E test lands in that test's isolated,
        // memory-only world instead of the shared default one.
        val gw = gameWorldRegistry.resolve(gameSessionId)

        val connectMsg =
            runCatching {
                    val firstFrame = socket.incoming.receive()
                    if (firstFrame is Frame.Binary) {
                        val msg = ClientMessageCodec.decode(firstFrame.readBytes())
                        if (msg is ClientMessage.Connect) msg else null
                    } else null
                }
                .getOrNull()

        val authResult =
            if (tokenStore != null) {
                val result = tokenStore.validate(connectMsg?.token ?: "")
                if (result == null) {
                    socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid token"))
                    return
                }
                result
            } else null

        val accountEmail: String =
            if (tokenStore != null) {
                authResult!!.email
            } else {
                val email = connectMsg?.userName ?: ""
                if (!email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                    socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid email"))
                    return
                }
                noAuthAccountStore?.getOrCreate(email)
                email
            }

        val persistence = gw.persistence

        // Resolve which character this account is opening the hub for. iteration 1: no
        // multi-character picker over the wire — the first character owned by the account is used
        // when none is named explicitly (persisted worlds only: a memory-only E2E world has no
        // "all players for this email" to scan, so it requires an explicit playerName — see below).
        val requestedName = connectMsg?.playerName?.takeIf { it.isNotBlank() }
        // A name reserved by `POST /api/admin/players` (browser E2E, same mechanism `onConnect`
        // consumes) wins over a stale persisted file for that name, exactly like onConnect does.
        val reserved = requestedName?.let { gw.reservedPlayers[it.lowercase()] }
        val candidate: PlayerState? =
            when {
                reserved != null ->
                    persistence?.loadPlayerState(requestedName)
                        ?: PlayerState(
                            id = reserved.id,
                            name = requestedName,
                            pos = Vec3(8f, 8f, 8f),
                            orientation = Orientation(0f, 0f),
                            email = accountEmail,
                        )
                persistence != null && requestedName != null ->
                    persistence.loadPlayerState(requestedName)
                persistence != null -> persistence.listPlayersByEmail(accountEmail).firstOrNull()
                else -> null
            }
        if (candidate == null ||
            (candidate.email.isNotEmpty() &&
                !candidate.email.equals(accountEmail, ignoreCase = true))) {
            socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unknown character"))
            return
        }

        val playerId = candidate.id
        val language =
            (lang ?: connectMsg?.preferredLanguage)?.let { if (it in i18n.locales) it else null }
                ?: candidate.language

        val online = gw.sessions[playerId]
        val session: PlayerSession
        val isCompanion: Boolean
        if (online != null) {
            session = online
            isCompanion = false
            session.attachExtra(socket)
        } else {
            session =
                PlayerSession(
                    id = playerId,
                    userName = connectMsg?.userName ?: candidate.name,
                    socket = socket,
                    state = candidate.copy(language = language),
                    permissions = authResult?.permissions ?: setOf("*"),
                    connectionId = connectMsg?.connectionId ?: "",
                )
            session.companion = true
            session.inventory.putAll(candidate.inventory)
            isCompanion = true
            gw.sessions.setCompanion(playerId, session)
        }

        log.info(
            "hub connected: {} name={} attach={}", playerId.take(8), candidate.name, !isCompanion)

        HubSyncs.sendAll(session, gw)

        try {
            for (frame in socket.incoming) {
                if (frame !is Frame.Binary) continue
                val msg = runCatching { ClientMessageCodec.decode(frame.readBytes()) }.getOrNull()
                if (msg != null) dispatch(gw, session, msg)
            }
        } finally {
            if (isCompanion) {
                gw.sessions.removeCompanion(playerId)
                PlayerPersister(persistence).save(session)
            } else {
                session.detachExtra(socket)
            }
        }
    }

    private suspend fun dispatch(gw: GameWorld, session: PlayerSession, msg: ClientMessage) {
        when (msg) {
            is ClientMessage.SendMail -> gw.mailManager?.handleSendMail(session, msg)
            is ClientMessage.MarkMailSeen -> gw.mailManager?.handleMarkSeen(session, msg.mailId)
            is ClientMessage.DeleteMail -> gw.mailManager?.handleDelete(session, msg.mailId)
            is ClientMessage.ClaimMailAttachments ->
                gw.mailManager?.handleClaimAttachments(session, msg.mailId)
            is ClientMessage.AuctionCreateListing ->
                gw.auctionManager?.createListing(
                    session,
                    msg.itemType,
                    msg.quantity,
                    msg.duration,
                    msg.startingPrice,
                    msg.buyNowPrice)
            is ClientMessage.AuctionPlaceBid ->
                gw.auctionManager?.placeBid(session, msg.listingId, msg.amount)
            is ClientMessage.AuctionBuyNow -> gw.auctionManager?.buyNow(session, msg.listingId)
            is ClientMessage.AuctionCancelListing ->
                gw.auctionManager?.cancel(session, msg.listingId)
            is ClientMessage.AuctionSetFilter -> gw.auctionManager?.setFilter(session, msg.filter)
            is ClientMessage.ClaimAbandon -> gw.claimManager.abandonClaim(session, msg.claimId)
            is ClientMessage.ClaimSetTrusted ->
                gw.claimManager.setTrusted(session, msg.claimId, msg.playerName, msg.trusted)
            is ClientMessage.ChatSend ->
                if (HUB_CHAT_CHANNELS(msg.channel))
                    gw.chatService.routeMessage(session, msg.channel, msg.text)
                else
                    session.send(
                        ServerMessage.Notification(
                            i18n.t(session.state.language, "hub:server:channel_unavailable")))
            else -> log.debug("hub: ignoring out-of-scope message {}", msg::class.simpleName)
        }
    }
}

/** Initial state pushed right after a `/hub` connection is accepted. */
object HubSyncs {
    suspend fun sendAll(session: PlayerSession, gw: GameWorld) {
        session.send(ServerMessage.WalletUpdate(session.state.wallet))
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        gw.mailManager?.let {
            session.send(ServerMessage.MailSync(it.loadForPlayer(session.state.name)))
        }
        gw.claimManager.sendSync(session)
        gw.auctionManager?.setFilter(session, AuctionFilter())
        session.send(
            ServerMessage.ChannelsSync(
                subscribedChannels = session.state.subscribedChannels,
                knownChannels = gw.chatChannelManager.listKnownChannels(),
            ))
    }
}
