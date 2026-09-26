package org.micoli.micraft.placeable.panel

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * What every client needs to display a panel: the resolved URL of its home page — a local
 * `/panels/<id>/<page>.html` route or an allowlisted external `https://` URL. [external] tells the
 * client which iframe sandbox flags to apply. The HTML itself never travels over the protocol.
 *
 * Every optional field is `@EncodeDefault(ALWAYS)` — the client-side JSON.parse expects the key to
 * always be present (kotlinx.serialization's default `Json` otherwise omits a field left at its
 * default value, e.g. a freshly-created panel's empty `pages`, turning it into `undefined` on the
 * TypeScript side instead of `{}`/`false`).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PanelInfo(
    val placeableId: String,
    val homeUrl: String,
    @EncodeDefault(ALWAYS) val external: Boolean = false,
)

/** Editor payload sent to a player allowed to edit the panel. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PanelEditData(
    val placeableId: String,
    @EncodeDefault(ALWAYS) val externalUrl: String = "",
    @EncodeDefault(ALWAYS) val pages: Map<String, String> = emptyMap(),
)
