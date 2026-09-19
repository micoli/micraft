package org.micoli.micraft.input

/**
 * Continuous movement actions polled every frame via `jsIsActionDown` (`mc.isActionDown` in
 * `keyboard.ts`) from `LocalPlayerController` — distinct from [ClientInputAction], which covers
 * discrete one-shot events. [wire] is the raw string that crosses the JS/Wasm boundary and must
 * match the keys used in `resources/config/keybindings.yaml` — this enum is the single source of
 * truth for it. `:server:generateMovementActionTypes` (`make gen-movement-actions`) regenerates
 * `app/webApp/ts-src/generated/input/movementActions.ts` from these entries, so the two sides can't
 * silently drift.
 */
enum class MovementAction(val wire: String) {
    FORWARD("forward"),
    BACKWARD("backward"),
    STRAFE_LEFT("strafe_left"),
    STRAFE_RIGHT("strafe_right"),
    ROTATE_LEFT("rotate_left"),
    ROTATE_RIGHT("rotate_right"),
    ROTATE_UP("rotate_up"),
    ROTATE_DOWN("rotate_down"),
    ASCEND("ascend"),
    DESCEND("descend"),
    CRAWL("crawl"),
    SNEAK("sneak"),
    SPEED_UP("speed_up"),
    SPEED_DOWN("speed_down"),
}
