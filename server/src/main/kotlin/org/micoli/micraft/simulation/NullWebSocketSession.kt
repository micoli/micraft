package org.micoli.micraft.simulation

import io.ktor.utils.io.InternalAPI
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Socket that goes nowhere. [org.micoli.micraft.game.session.PlayerSession] requires one, and the
 * world simulator's players are not connected to any client — outgoing frames are dropped.
 */
@Suppress("OVERRIDE_DEPRECATION")
@OptIn(InternalAPI::class)
class NullWebSocketSession : DefaultWebSocketSession {
    override val coroutineContext: CoroutineContext = Dispatchers.Unconfined + Job()
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    private val outgoingChannel: Channel<Frame> = Channel(Channel.CONFLATED)
    private val incomingChannel: Channel<Frame> = Channel(Channel.CONFLATED)
    override val outgoing: SendChannel<Frame> = outgoingChannel
    override val incoming: ReceiveChannel<Frame> = incomingChannel
    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override var pingIntervalMillis: Long = 0L
    override var timeoutMillis: Long = 15_000L
    override val closeReason: Deferred<CloseReason?> = CompletableDeferred(null)

    override suspend fun flush() {}

    override fun terminate() {
        outgoingChannel.close()
        incomingChannel.close()
    }

    override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {}
}
