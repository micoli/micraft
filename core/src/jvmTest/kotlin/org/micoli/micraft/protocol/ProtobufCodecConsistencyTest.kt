package org.micoli.micraft.protocol

import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProtobufCodecConsistencyTest {
    @Test
    fun serverRegistryAnnotationsMatchIndex() {
        ServerMessageCodec.registry.forEachIndexed { idx, entry ->
            val ann = entry.klass.findAnnotation<ProtoId>()
            assertNotNull(ann, "Class ${entry.klass.simpleName} is missing @ProtoId annotation")
            assertEquals(
                idx,
                ann.id,
                "Class ${entry.klass.simpleName} has @ProtoId(${ann.id}) but is at index $idx in ServerMessageCodec.registry",
            )
        }
    }

    @Test
    fun clientRegistryAnnotationsMatchIndex() {
        ClientMessageCodec.registry.forEachIndexed { idx, entry ->
            val ann = entry.klass.findAnnotation<ProtoId>()
            assertNotNull(ann, "Class ${entry.klass.simpleName} is missing @ProtoId annotation")
            assertEquals(
                idx,
                ann.id,
                "Class ${entry.klass.simpleName} has @ProtoId(${ann.id}) but is at index $idx in ClientMessageCodec.registry",
            )
        }
    }
}
