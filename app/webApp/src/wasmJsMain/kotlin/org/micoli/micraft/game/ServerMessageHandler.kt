package org.micoli.micraft.game

import org.micoli.micraft.protocol.ServerMessage

fun interface ServerMessageHandler {
    fun handle(msg: ServerMessage)
}

inline fun <reified T : ServerMessage> typedHandler(
    noinline f: (T) -> Unit,
): ServerMessageHandler = ServerMessageHandler { f(it as T) }
