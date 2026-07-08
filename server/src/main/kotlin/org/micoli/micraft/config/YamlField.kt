package org.micoli.micraft.config

import kotlinx.serialization.KSerializer

class YamlField<T>(
    val key: String,
    val value: T,
    val serializer: KSerializer<T>,
    val present: Boolean
)
