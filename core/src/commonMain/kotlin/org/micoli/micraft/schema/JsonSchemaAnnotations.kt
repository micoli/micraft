package org.micoli.micraft.schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

/**
 * Marks a root DTO whose JSON Schema is generated into [file] under
 * server/src/main/resources/schemas/.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonSchemaRoot(
    val file: String,
    val root: JsonSchemaRootShape = JsonSchemaRootShape.OBJECT
)

enum class JsonSchemaRootShape {
    OBJECT,
    MAP_OF,
}

/**
 * Adds JSON Schema validation keywords on top of the schema already derived from the property's
 * Kotlin type — it refines, it never replaces. Unset fields (NaN / -1 / empty) are omitted.
 *
 * `item*` fields apply to the `items` sub-schema of a List/Set property.
 */
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonSchemaConstraint(
    val minimum: Double = Double.NaN,
    val maximum: Double = Double.NaN,
    val exclusiveMinimum: Double = Double.NaN,
    val minLength: Int = -1,
    val maxLength: Int = -1,
    val pattern: String = "",
    val enum: Array<String> = [],
    val minItems: Int = -1,
    val maxItems: Int = -1,
    val itemMinimum: Double = Double.NaN,
    val itemMaximum: Double = Double.NaN,
    val itemExclusiveMinimum: Double = Double.NaN,
    val itemPattern: String = "",
)

/**
 * Marks a type (typically a sealed class serialized polymorphically) whose real Kotlin shape should
 * not be walked. Emits the same loose `{type, ...payload}` discriminator-shaped object
 * kotlinx.serialization's default polymorphic encoding produces, wherever this type appears
 * (including nested in List/List<List<>>/nullable — the surrounding shape is still derived
 * normally).
 */
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonSchemaOpaque

/** Emits additionalProperties:true for this class instead of the default false. */
@SerialInfo
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonSchemaOpen
