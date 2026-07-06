package org.micoli.micraft.support

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

@Suppress("OVERRIDE_DEPRECATION")
@OptIn(InternalAPI::class)
class FakeWebSocketSession : DefaultWebSocketSession {
    override val coroutineContext: CoroutineContext = Dispatchers.Unconfined + Job()
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    val outgoingChannel: Channel<Frame> = Channel(Channel.UNLIMITED)
    val incomingChannel: Channel<Frame> = Channel(Channel.UNLIMITED)
    override val outgoing: SendChannel<Frame> = outgoingChannel
    override val incoming: ReceiveChannel<Frame> = incomingChannel
    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override var pingIntervalMillis: Long = 0L
    override var timeoutMillis: Long = 15_000L
    override val closeReason: Deferred<CloseReason?> = CompletableDeferred(null)

    override suspend fun flush() {}

    override fun terminate() {}

    override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {}
}
