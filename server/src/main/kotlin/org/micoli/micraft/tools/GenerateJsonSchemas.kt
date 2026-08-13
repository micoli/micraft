@file:OptIn(ExperimentalSerializationApi::class)

package org.micoli.micraft.tools

import io.github.classgraph.ClassGraph
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.system.exitProcess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaOpaque
import org.micoli.micraft.schema.JsonSchemaOpen
import org.micoli.micraft.schema.JsonSchemaRoot
import org.micoli.micraft.schema.JsonSchemaRootShape

private val jsonWriter = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

private val OPAQUE_SCHEMA = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject { put("type", buildJsonObject { put("type", "string") }) })
    put("additionalProperties", true)
}

internal class SchemaGenerationException(message: String) : RuntimeException(message)

fun main(args: Array<String>) {
    val checkOnly = "--check" in args
    val schemasDir = File("server/src/main/resources/schemas")

    val roots =
        ClassGraph().enableAnnotationInfo().acceptPackages("org.micoli.micraft").scan().use { result
            ->
            result.getClassesWithAnnotation(JsonSchemaRoot::class.java).map {
                it.loadClass().kotlin
            }
        }

    if (roots.isEmpty()) {
        System.err.println("ERROR: no class annotated with @JsonSchemaRoot found.")
        exitProcess(1)
    }

    var outOfDate = false
    for (kClass in roots.sortedBy { it.qualifiedName }) {
        val annotation = kClass.java.getAnnotation(JsonSchemaRoot::class.java)
        val descriptor = kClass.rootSerializer().descriptor
        val objectSchema =
            try {
                schemaForClass(descriptor)
            } catch (e: SchemaGenerationException) {
                System.err.println(
                    "ERROR generating ${annotation.file} from ${kClass.qualifiedName}: ${e.message}")
                exitProcess(1)
            }
        val root =
            when (annotation.root) {
                JsonSchemaRootShape.OBJECT -> objectSchema
                JsonSchemaRootShape.MAP_OF ->
                    buildJsonObject {
                        put("type", "object")
                        put("additionalProperties", objectSchema)
                    }
            }
        val document = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            root.forEach { (k, v) -> put(k, v) }
        }
        val text = jsonWriter.encodeToString(JsonElement.serializer(), document) + "\n"
        val target = File(schemasDir, annotation.file)

        if (checkOnly) {
            val current = if (target.exists()) target.readText() else ""
            if (current != text) {
                System.err.println("OUT OF DATE: ${target.path} (source: ${kClass.qualifiedName})")
                outOfDate = true
            }
        } else {
            target.writeText(text)
            println("Wrote ${target.path} (from ${kClass.qualifiedName})")
        }
    }

    if (checkOnly) {
        if (outOfDate) {
            System.err.println(
                "JSON schemas out of date. Run: make dc CMD=\"./gradlew :server:generateJsonSchemas\"")
            exitProcess(1)
        }
        println("OK. ${roots.size} JSON schema(s) up to date.")
    }
}

private fun KClass<*>.rootSerializer() = serializer(this.createType())

internal fun schemaFor(descriptor: SerialDescriptor, stack: Set<String> = emptySet()): JsonObject {
    if (descriptor.annotations.any { it is JsonSchemaOpaque }) {
        return OPAQUE_SCHEMA
    }
    return when (descriptor.kind) {
        PrimitiveKind.STRING,
        PrimitiveKind.CHAR -> buildJsonObject { put("type", "string") }
        PrimitiveKind.BOOLEAN -> buildJsonObject { put("type", "boolean") }
        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT,
        PrimitiveKind.LONG -> buildJsonObject { put("type", "integer") }
        PrimitiveKind.FLOAT,
        PrimitiveKind.DOUBLE -> buildJsonObject { put("type", "number") }
        SerialKind.ENUM ->
            buildJsonObject {
                put("type", "string")
                put(
                    "enum",
                    buildJsonArray { descriptor.elementNames.forEach { add(JsonPrimitive(it)) } })
            }
        StructureKind.LIST ->
            buildJsonObject {
                put("type", "array")
                put("items", childSchema(descriptor, 0, stack))
            }
        StructureKind.MAP ->
            buildJsonObject {
                put("type", "object")
                put("additionalProperties", childSchema(descriptor, 1, stack))
            }
        StructureKind.CLASS,
        StructureKind.OBJECT -> schemaForClass(descriptor, stack)
        else ->
            throw SchemaGenerationException(
                "unsupported SerialKind ${descriptor.kind} for ${descriptor.serialName}; " +
                    "annotate the class with @JsonSchemaOpaque")
    }
}

internal fun schemaForClass(
    descriptor: SerialDescriptor,
    stack: Set<String> = emptySet()
): JsonObject {
    if (descriptor.annotations.any { it is JsonSchemaOpaque }) {
        return OPAQUE_SCHEMA
    }
    if (descriptor.serialName in stack) {
        throw SchemaGenerationException(
            "cyclic type ${descriptor.serialName}; not supported, use @JsonSchemaOpaque")
    }
    val nextStack = stack + descriptor.serialName
    val required = mutableListOf<String>()
    val properties = buildJsonObject {
        for (i in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(i)
            put(name, propertySchema(descriptor, i, nextStack))
            if (!descriptor.isElementOptional(i)) {
                required.add(name)
            }
        }
    }
    val open = descriptor.annotations.any { it is JsonSchemaOpen }
    return buildJsonObject {
        put("type", "object")
        if (required.isNotEmpty()) {
            put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        }
        put("additionalProperties", open)
        put("properties", properties)
    }
}

private fun propertySchema(parent: SerialDescriptor, index: Int, stack: Set<String>): JsonElement {
    val child = parent.getElementDescriptor(index)
    val schema = schemaFor(child, stack)
    val nullified = if (child.isNullable) nullify(schema) else schema
    val constraint =
        parent.getElementAnnotations(index).filterIsInstance<JsonSchemaConstraint>().firstOrNull()
            ?: return nullified
    return applyConstraint(nullified, constraint)
}

private fun childSchema(parent: SerialDescriptor, index: Int, stack: Set<String>): JsonElement {
    val child = parent.getElementDescriptor(index)
    val schema = schemaFor(child, stack)
    return if (child.isNullable) nullify(schema) else schema
}

private fun nullify(schema: JsonObject): JsonElement {
    val type = schema["type"]
    return if (type is JsonPrimitive && schema.size == 1) {
        buildJsonObject {
            put(
                "type",
                buildJsonArray {
                    add(JsonPrimitive(type.content))
                    add(JsonPrimitive("null"))
                })
        }
    } else {
        buildJsonObject {
            put(
                "oneOf",
                buildJsonArray {
                    add(buildJsonObject { put("type", "null") })
                    add(schema)
                })
        }
    }
}

private fun applyConstraint(schema: JsonElement, c: JsonSchemaConstraint): JsonElement {
    if (schema !is JsonObject) return schema
    val map = schema.toMutableMap()
    if (!c.minimum.isNaN()) map["minimum"] = numberPrimitive(c.minimum)
    if (!c.maximum.isNaN()) map["maximum"] = numberPrimitive(c.maximum)
    if (!c.exclusiveMinimum.isNaN()) map["exclusiveMinimum"] = numberPrimitive(c.exclusiveMinimum)
    if (c.minLength >= 0) map["minLength"] = JsonPrimitive(c.minLength)
    if (c.maxLength >= 0) map["maxLength"] = JsonPrimitive(c.maxLength)
    if (c.pattern.isNotEmpty()) map["pattern"] = JsonPrimitive(c.pattern)
    if (c.enum.isNotEmpty()) map["enum"] = JsonArray(c.enum.map { JsonPrimitive(it) })
    if (c.minItems >= 0) map["minItems"] = JsonPrimitive(c.minItems)
    if (c.maxItems >= 0) map["maxItems"] = JsonPrimitive(c.maxItems)
    val hasItemConstraint =
        !c.itemMinimum.isNaN() ||
            !c.itemMaximum.isNaN() ||
            !c.itemExclusiveMinimum.isNaN() ||
            c.itemPattern.isNotEmpty()
    if (hasItemConstraint) {
        val items = ((map["items"] as? JsonObject) ?: JsonObject(emptyMap())).toMutableMap()
        if (!c.itemMinimum.isNaN()) items["minimum"] = numberPrimitive(c.itemMinimum)
        if (!c.itemMaximum.isNaN()) items["maximum"] = numberPrimitive(c.itemMaximum)
        if (!c.itemExclusiveMinimum.isNaN())
            items["exclusiveMinimum"] = numberPrimitive(c.itemExclusiveMinimum)
        if (c.itemPattern.isNotEmpty()) items["pattern"] = JsonPrimitive(c.itemPattern)
        map["items"] = JsonObject(items)
    }
    return JsonObject(map)
}

private fun numberPrimitive(value: Double): JsonPrimitive =
    if (value == Math.floor(value) && !value.isInfinite()) JsonPrimitive(value.toLong())
    else JsonPrimitive(value)
