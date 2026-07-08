package org.micoli.micraft.config

class YamlSection(
    val key: String,
    val present: Boolean = true,
    val fields: List<YamlField<*>> = emptyList(),
    val subsections: List<YamlSection> = emptyList(),
)
