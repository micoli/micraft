package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
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

private val DEFAULT_SECTIONS: Map<String, Map<String, List<String>>> =
    Yaml.default.decodeFromString(SECTION_SERIALIZER, DEFAULT_YAML)

fun defaultKeyBindings(): Map<String, List<String>> = buildMap {
    for ((_, actions) in DEFAULT_SECTIONS) putAll(actions)
}

/**
 * Two levels of dynamic maps (category -> action -> keys), not a fixed data class, so this builds
 * the [YamlSection] tree by hand instead of via [yamlConfigSection]/[yamlMapSection].
 */
private fun keyBindingsSection(
    defaults: Map<String, Map<String, List<String>>>,
    node: YamlNode?,
): YamlSection =
    YamlSection(
        key = "",
        subsections =
            defaults.map { (section, actions) ->
                YamlSection(
                    key = section,
                    present = presentInYaml(node, section),
                    fields =
                        actions.map { (action, keys) ->
                            YamlField(
                                action,
                                keys,
                                ListSerializer(String.serializer()),
                                presentInYaml(node, section, action))
                        },
                )
            },
    )

fun loadKeyBindings(path: Path): Map<String, List<String>> {
    val originalText = if (path.exists()) path.readText() else ""
    val sections =
        if (path.exists()) {
            validateYamlConfig(path, "keybindings.schema.json")
            runCatching { Yaml.default.decodeFromString(SECTION_SERIALIZER, originalText) }
                .getOrElse { e ->
                    log.warn("Failed to parse keybindings.yaml ({}), using defaults", e.message)
                    DEFAULT_SECTIONS
                }
        } else {
            log.info("No keybindings.yaml at {}, creating with defaults", path.toAbsolutePath())
            DEFAULT_SECTIONS
        }
    path.parent?.createDirectories()
    if (originalText.isBlank()) {
        path.writeText(spliceMissingAsComments("", keyBindingsSection(DEFAULT_SECTIONS, null)))
    } else {
        runCatching { Yaml.default.parseToYamlNode(originalText) }
            .onSuccess { node ->
                path.writeText(
                    spliceMissingAsComments(
                        originalText, keyBindingsSection(DEFAULT_SECTIONS, node)))
            }
            .onFailure {
                log.warn(
                    "keybindings.yaml has unparseable structure, leaving file untouched: {}",
                    it.message)
            }
    }
    return buildMap { for ((_, actions) in sections) putAll(actions) }
}
