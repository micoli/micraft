package org.micoli.micraft.placeable.panel

object PanelConstants {
    const val DEFAULT_PIXEL_WIDTH = 640
    const val DEFAULT_PIXEL_HEIGHT = 480

    const val MAX_PAGES = 16
    const val MAX_PAGE_NAME_LENGTH = 48
    const val MAX_PAGE_HTML_LENGTH = 16_384
    const val MAX_URL_LENGTH = 512

    const val INDEX_PAGE = "index"

    /** Distance (blocks) under which a panel's iframe is instantiated client-side. */
    const val ACTIVE_RADIUS = 16f

    /** Max number of simultaneously live iframes. */
    const val MAX_ACTIVE = 4

    /** Max distance (blocks) from which a panel can be focused. */
    const val FOCUS_DISTANCE = 4f

    fun pageUrl(placeableId: String, page: String = INDEX_PAGE): String =
        "/panels/$placeableId/$page.html"
}
