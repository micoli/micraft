package org.micoli.micraft.game.vehicle

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaRoot
import org.micoli.micraft.schema.JsonSchemaRootShape

@Serializable
@JsonSchemaRoot(file = "vehicles.schema.json", root = JsonSchemaRootShape.MAP_OF)
data class VehicleYamlEntry(
    val bbmodelFile: String = "",
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val width: Float = 0.8f,
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val height: Float = 0.8f,
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val speed: Float = 2f,
)
