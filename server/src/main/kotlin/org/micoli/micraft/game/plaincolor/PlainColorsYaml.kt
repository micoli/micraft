package org.micoli.micraft.game.plaincolor

import kotlinx.serialization.Serializable

/** Yaml shape of `config/plain_colors.yaml`: color name → uppercase RRGGBB hex (no '#'). */
@Serializable data class PlainColorsYaml(val plainColors: Map<String, String> = emptyMap())
