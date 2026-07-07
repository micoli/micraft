package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer

class YamlField<T>(
    val key: String,
    val value: T,
    val serializer: KSerializer<T>,
    val present: Boolean
)

class YamlSection(
    val key: String,
    val present: Boolean = true,
    val fields: List<YamlField<*>> = emptyList(),
    val subsections: List<YamlSection> = emptyList(),
)

/**
 * Builds a [YamlSection] listing every field of [T] with its effective value from [overridden],
 * marked `present` when the matching field on [override] (a class sharing [T]'s field names, with
 * nullable equivalents) is non-null. Used to document current defaults as `# key: value` comments
 * for fields the user hasn't explicitly overridden.
 */
inline fun <reified T : Any> yamlOverrideSection(overridden: T, override: Any): YamlSection {
    val entryProps =
        T::class
            .memberProperties
            .associateBy { it.name }
            .mapValues { it.value.apply { isAccessible = true } }
    val overrideProps =
        override::class
            .memberProperties
            .associateBy { it.name }
            .mapValues { it.value.apply { isAccessible = true } }
    val fieldNames = serializer<T>().descriptor.elementNames.toList()
    return YamlSection(
        key = "",
        fields =
            fieldNames.map { name ->
                val prop = entryProps.getValue(name)
                val value = prop.get(overridden)
                val present = overrideProps.getValue(name).call(override) != null
                YamlField(name, value, serializer(prop.returnType), present)
            },
    )
}

/**
 * Recursively builds a [YamlSection] tree from [value] (an instance of [kClass]): nested
 * `@Serializable` properties become subsections, other properties become fields. Presence is looked
 * up in [node] by path, so this mirrors hand-written per-property [YamlSection] building for any
 * nested config data class without listing its fields manually.
 */
fun <T : Any> yamlConfigSection(
    kClass: KClass<T>,
    key: String,
    value: T,
    node: YamlNode?,
    path: List<String> = emptyList(),
): YamlSection {
    val checkPath = if (key.isEmpty()) path else path + key
    val props =
        kClass.memberProperties
            .associateBy { it.name }
            .mapValues { it.value.apply { isAccessible = true } }
    val fieldNames = serializer(kClass.createType()).descriptor.elementNames.toList()
    val fields = mutableListOf<YamlField<*>>()
    val subsections = mutableListOf<YamlSection>()
    for (name in fieldNames) {
        val prop = props.getValue(name)
        val classifier = prop.returnType.classifier as? KClass<*>
        if (classifier != null &&
            classifier.findAnnotation<Serializable>() != null &&
            !classifier.java.isEnum) {
            val nested =
                prop.get(value)
                    ?: classifier.primaryConstructor!!
                        .apply { isAccessible = true }
                        .callBy(emptyMap())
            @Suppress("UNCHECKED_CAST")
            subsections +=
                yamlConfigSection(classifier as KClass<Any>, name, nested, node, checkPath)
        } else {
            val present = presentInYaml(node, *(checkPath + name).toTypedArray())
            fields += YamlField(name, prop.get(value), serializer(prop.returnType), present)
        }
    }
    return YamlSection(
        key = key,
        present = presentInYaml(node, *checkPath.toTypedArray()),
        fields = fields,
        subsections = subsections,
    )
}

/**
 * Fuses [decoded] (from the user's file) with [default] (from the shipped resources file): a field
 * absent from [node] takes its value from [default] instead of [decoded]'s own constructor default,
 * so defaults live only in the resources yaml, never in Kotlin code. Recurses into nested
 * `@Serializable` properties exactly like [yamlConfigSection].
 */
fun <T : Any> mergeConfig(
    kClass: KClass<T>,
    decoded: T,
    default: T,
    node: YamlNode?,
    path: List<String> = emptyList(),
): T {
    val props =
        kClass.memberProperties
            .associateBy { it.name }
            .mapValues { it.value.apply { isAccessible = true } }
    val ctor = kClass.primaryConstructor!!.apply { isAccessible = true }
    val args =
        ctor.parameters.associateWith { param ->
            val name = param.name!!
            val prop = props.getValue(name)
            val classifier = prop.returnType.classifier as? KClass<*>
            if (classifier != null &&
                classifier.findAnnotation<Serializable>() != null &&
                !classifier.java.isEnum) {
                @Suppress("UNCHECKED_CAST") val kc = classifier as KClass<Any>
                val nestedCtor = kc.primaryConstructor!!.apply { isAccessible = true }
                val decodedNested = prop.get(decoded) ?: nestedCtor.callBy(emptyMap())
                val defaultNested = prop.get(default) ?: nestedCtor.callBy(emptyMap())
                mergeConfig(kc, decodedNested, defaultNested, node, path + name)
            } else {
                if (presentInYaml(node, *(path + name).toTypedArray())) prop.get(decoded)
                else prop.get(default)
            }
        }
    return ctor.callBy(args)
}

/**
 * Map-of-entries variant of [mergeConfig]: merges by key, fusing each entry present on both sides
 * field-by-field, keeping user-only entries as-is, and adding resources-only entries untouched.
 */
inline fun <reified T : Any> mergeMapConfig(
    decodedMap: Map<String, T>,
    defaultMap: Map<String, T>,
    node: YamlNode?,
): Map<String, T> =
    (defaultMap.keys + decodedMap.keys).associateWith { key ->
        val decodedEntry = decodedMap[key]
        val defaultEntry = defaultMap[key]
        when {
            decodedEntry != null && defaultEntry != null ->
                mergeConfig(T::class, decodedEntry, defaultEntry, node, listOf(key))
            decodedEntry != null -> decodedEntry
            else -> defaultEntry!!
        }
    }

/**
 * Builds a [YamlSection] for a map of homogeneous entries (e.g. items, recipes, drop tables): one
 * subsection per map key, built via [yamlConfigSection] so both whole-entry presence and per-field
 * presence within an existing entry are checked against [node].
 */
inline fun <reified T : Any> yamlMapSection(entries: Map<String, T>, node: YamlNode?): YamlSection =
    YamlSection(
        key = "",
        subsections = entries.map { (key, value) -> yamlConfigSection(T::class, key, value, node) },
    )

fun presentInYaml(node: YamlNode?, vararg path: String): Boolean {
    var current: YamlNode? = node
    for (key in path) {
        current = (current as? YamlMap)?.get(key)
        if (current == null) return false
    }
    return true
}

/**
 * Rewrites [originalText] so keys already present are left byte-identical, and keys missing from
 * [root] (falling back to code defaults) are appended as `# key: value` comments in the right
 * section, without touching or reordering any existing line.
 */
fun spliceMissingAsComments(originalText: String, root: YamlSection): String {
    val lines =
        if (originalText.isBlank()) mutableListOf() else originalText.lines().toMutableList()
    processChildren(lines, 0, 0, lines.size, root.fields, root.subsections)
    return lines.joinToString("\n")
}

private fun processChildren(
    lines: MutableList<String>,
    indent: Int,
    from: Int,
    to: Int,
    fields: List<YamlField<*>>,
    subsections: List<YamlSection>,
): Int {
    var end = to
    val missingFieldLines =
        fields
            .filter { !it.present && !keyExistsInRange(lines, from, end, indent, it.key) }
            .flatMap { formatCommentedField(indent, it) }
    if (missingFieldLines.isNotEmpty()) {
        lines.addAll(end, missingFieldLines)
        end += missingFieldLines.size
    }
    for (sub in subsections) {
        if (sub.present) {
            val body = findSectionBody(lines, from, end, indent, sub.key)
            if (body != null) {
                val newBodyEnd =
                    processChildren(
                        lines, indent + 1, body.first, body.last + 1, sub.fields, sub.subsections)
                end += newBodyEnd - (body.last + 1)
            }
        } else if (!keyExistsInRange(lines, from, end, indent, sub.key)) {
            val block = renderFullyCommented(indent, sub)
            lines.addAll(end, block)
            end += block.size
        }
    }
    return end
}

/**
 * True if [key] already appears as its own line at [indent] within [from, to), active or commented.
 */
private fun keyExistsInRange(
    lines: List<String>,
    from: Int,
    to: Int,
    indent: Int,
    key: String
): Boolean {
    var i = from
    while (i < to) {
        val line = lines[i]
        val trimmed = line.trimStart(' ')
        val lineIndent = line.length - trimmed.length
        val content =
            if (trimmed.startsWith("#")) trimmed.removePrefix("#").trimStart(' ') else trimmed
        if (lineIndent == indent * 2 && (content == "$key:" || content.startsWith("$key: "))) {
            return true
        }
        i++
    }
    return false
}

private fun findSectionBody(
    lines: List<String>,
    from: Int,
    to: Int,
    indent: Int,
    key: String
): IntRange? {
    var i = from
    while (i < to) {
        val line = lines[i]
        val trimmed = line.trimStart(' ')
        val lineIndent = line.length - trimmed.length
        if (lineIndent == indent * 2 &&
            !trimmed.startsWith("#") &&
            (trimmed == "$key:" || trimmed.startsWith("$key: "))) {
            var j = i + 1
            while (j < to) {
                val l = lines[j]
                if (l.isBlank()) {
                    j++
                    continue
                }
                val lTrim = l.trimStart(' ')
                val lIndent = l.length - lTrim.length
                if (lIndent <= indent * 2) break
                j++
            }
            return (i + 1) until j
        }
        i++
    }
    return null
}

@Suppress("UNCHECKED_CAST")
private fun formatCommentedField(indent: Int, field: YamlField<*>): List<String> {
    val serializer = field.serializer as KSerializer<Any?>
    val formatted = Yaml.default.encodeToString(serializer, field.value).trimEnd('\n')
    val prefix = "  ".repeat(indent)
    val valueLines = formatted.lines()
    return if (valueLines.size == 1) {
        listOf("$prefix# ${field.key}: ${valueLines[0]}")
    } else {
        listOf("$prefix# ${field.key}:") + valueLines.map { "$prefix# $it" }
    }
}

private fun renderFullyCommented(indent: Int, section: YamlSection): List<String> {
    val prefix = "  ".repeat(indent)
    val header = listOf("$prefix# ${section.key}:")
    val fieldLines = section.fields.flatMap { formatCommentedField(indent + 1, it) }
    val subLines = section.subsections.flatMap { renderFullyCommented(indent + 1, it) }
    return header + fieldLines + subLines
}
