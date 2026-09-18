package org.micoli.micraft.input

import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ClientMessage

/** Creative-mode payloads — grouped so `LocalPlayerController` can dispatch them in one branch. */
sealed interface CreativeEvent

data class CreativePlace(
    val x: Int,
    val y: Int,
    val z: Int,
    val itemId: String,
    val rotation: Int,
) : ClientInputEvent(), CreativeEvent

data class CreativeFocus(val x: Float, val z: Float) : ClientInputEvent(), CreativeEvent

data class CreativeBreak(val x: Int, val y: Int, val z: Int) : ClientInputEvent(), CreativeEvent

data class ScenePreviewRequest(val sceneId: String) : ClientInputEvent(), CreativeEvent

/** `"<x>,<y>,<z>,<itemId>[,<rotation>]"` — null (dropped) when a required field is missing. */
internal fun parseCreativePlace(payload: String): ClientInputEvent? {
    val parts = payload.split(",")
    val x = parts.getOrNull(0)?.toIntOrNull()
    val y = parts.getOrNull(1)?.toIntOrNull()
    val z = parts.getOrNull(2)?.toIntOrNull()
    val itemId = parts.getOrNull(3)
    val rotation = parts.getOrNull(4)?.toIntOrNull() ?: 0
    if (x == null || y == null || z == null || itemId.isNullOrEmpty()) return null
    return CreativePlace(x, y, z, itemId, rotation)
}

/** `"<x>,<z>"` */
internal fun parseCreativeFocus(payload: String): ClientInputEvent? {
    val parts = payload.split(",")
    val x = parts.getOrNull(0)?.toFloatOrNull()
    val z = parts.getOrNull(1)?.toFloatOrNull()
    if (x == null || z == null) return null
    return CreativeFocus(x, z)
}

/** `"<x>,<y>,<z>"` */
internal fun parseCreativeBreak(payload: String): ClientInputEvent? {
    val parts = payload.split(",")
    val x = parts.getOrNull(0)?.toIntOrNull()
    val y = parts.getOrNull(1)?.toIntOrNull()
    val z = parts.getOrNull(2)?.toIntOrNull()
    if (x == null || y == null || z == null) return null
    return CreativeBreak(x, y, z)
}

class CreativeEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: CreativeEvent) {
        when (event) {
            is CreativePlace ->
                ctx.outMessages.trySend(
                    ClientMessage.BlockPlace(
                        BlockPos(event.x, event.y, event.z),
                        ItemType(event.itemId),
                        event.rotation.toByte()))
            is CreativeFocus ->
                ctx.outMessages.trySend(ClientMessage.CreativeCameraFocus(event.x, event.z))
            is CreativeBreak ->
                ctx.outMessages.trySend(
                    ClientMessage.BlockBreakStart(BlockPos(event.x, event.y, event.z)))
            is ScenePreviewRequest ->
                ctx.outMessages.trySend(ClientMessage.RequestScenePreview(event.sceneId))
        }
    }
}
