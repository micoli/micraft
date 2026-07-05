package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("KeyBindingsConfig")

private val SECTION_SERIALIZER =
    MapSerializer(
        String.serializer(),
        MapSerializer(String.serializer(), ListSerializer(String.serializer())))

private val DEFAULT_YAML =
    """
combat:
  combat_target_cycle: [Tab]

movement:
  forward: [KeyW, ArrowUp]
  backward: [KeyS, ArrowDown]
  strafe_right: [KeyD, ArrowRight]
  strafe_left: [KeyA, ArrowLeft]
  rotate_left: [KeyQ]
  rotate_right: [KeyE]
  sneak: [ShiftLeft]
  crawl: [ControlLeft]
  auto_forward: ["KeyW+KeyW", "ArrowUp+ArrowUp"]

flight:
  fly_toggle: ["Space+Space"]
  ascend: [Space]
  descend: [ShiftLeft]
  speed_up: [KeyP]
  speed_down: [KeyO]

ui:
  view_toggle: [KeyF]
  hud_mode_cycle: [KeyH]
  inventory: [KeyI]
  character: [KeyY]
  undo: ["Ctrl+KeyZ", "Cmd+KeyZ"]
  minimap_zoom_in: [k]
  minimap_zoom_out: [l]
  ingame_map: [m]
  layout_editor: [KeyG]
  dump_stats: [KeyV]

hotbar:
  slot_1: [Digit1]
  slot_2: [Digit2]
  slot_3: [Digit3]
  slot_4: [Digit4]
  slot_5: [Digit5]
  slot_6: [Digit6]
  slot_7: [Digit7]
  slot_8: [Digit8]
  slot_9: [Digit9]
  slot_10: [Digit0]
"""
        .trimIndent()

fun defaultKeyBindings(): Map<String, List<String>> = buildMap {
    for ((_, actions) in Yaml.default.decodeFromString(SECTION_SERIALIZER, DEFAULT_YAML)) putAll(
        actions)
}

fun loadKeyBindings(path: Path): Map<String, List<String>> {
    if (!path.exists()) {
        log.info("No keybindings.yaml at {}, creating with defaults", path.toAbsolutePath())
        path.parent?.createDirectories()
        path.writeText(DEFAULT_YAML)
    }
    validateYamlConfig(path, "keybindings.schema.json")
    val sections =
        runCatching { Yaml.default.decodeFromString(SECTION_SERIALIZER, path.readText()) }
            .getOrElse { e ->
                log.warn("Failed to parse keybindings.yaml ({}), using defaults", e.message)
                return buildMap {
                    for ((_, actions) in
                        Yaml.default.decodeFromString(SECTION_SERIALIZER, DEFAULT_YAML)) putAll(
                        actions)
                }
            }
    return buildMap { for ((_, actions) in sections) putAll(actions) }
}
