package org.micoli.micraft.input

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import org.micoli.micraft.protocol.ClientMessage

/**
 * What every per-concern [ClientInputEvent] handler needs — deliberately narrow (no reference to
 * the wasm/JS bridge or the full `LocalPlayerController`) so each handler runs the same on any
 * target and can be exercised without a browser/Wasm runtime.
 */
class ClientEventContext(
    val outMessages: Channel<ClientMessage>,
    val currentCombatTargetId: () -> String?,
    /** Mirrors the previous inline `jsError("$label decode failed: $it")` calls. */
    val onDecodeFailure: (label: String, error: Throwable) -> Unit = { _, _ -> },
) {
    inline fun <reified T : ClientMessage> decodeSilently(json: String) {
        runCatching { outMessages.trySend(Json.decodeFromString<T>(json)) }
    }

    inline fun <reified T : ClientMessage> decodeLogged(json: String, label: String) {
        runCatching { outMessages.trySend(Json.decodeFromString<T>(json)) }
            .onFailure { onDecodeFailure(label, it) }
    }
}
