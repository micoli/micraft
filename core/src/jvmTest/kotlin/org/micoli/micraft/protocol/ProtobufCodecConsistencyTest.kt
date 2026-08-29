package org.micoli.micraft.protocol

import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProtobufCodecConsistencyTest {
    private fun leafSubclasses(root: KClass<*>): List<KClass<*>> =
        root.sealedSubclasses.flatMap { if (it.isSealed) leafSubclasses(it) else listOf(it) }

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

    @Test
    fun everyServerMessageSubclassIsRegisteredExactlyOnce() =
        assertAllRegistered(ServerMessage::class, ServerMessageCodec.registry.map { it.klass })

    @Test
    fun everyClientMessageSubclassIsRegisteredExactlyOnce() =
        assertAllRegistered(ClientMessage::class, ClientMessageCodec.registry.map { it.klass })

    private fun assertAllRegistered(root: KClass<*>, registered: List<KClass<*>>) {
        val expected = leafSubclasses(root).toSet()
        val missing = expected - registered.toSet()
        val unknown = registered.toSet() - expected
        val duplicates = registered.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(
            missing.isEmpty(),
            "${root.simpleName} subclasses not in the codec registry: ${missing.map { it.simpleName }}",
        )
        assertTrue(
            unknown.isEmpty(),
            "Codec registry entries that are not ${root.simpleName} leaves: $unknown")
        assertTrue(
            duplicates.isEmpty(),
            "Codec registry lists the same class twice: ${duplicates.map { it.simpleName }}")
    }

    @Test fun serverProtoIdsAreContiguousFromZero() = assertContiguousProtoIds(ServerMessage::class)

    @Test fun clientProtoIdsAreContiguousFromZero() = assertContiguousProtoIds(ClientMessage::class)

    private fun assertContiguousProtoIds(root: KClass<*>) {
        val ids =
            leafSubclasses(root).map { klass ->
                val ann = klass.findAnnotation<ProtoId>()
                assertNotNull(ann, "Class ${klass.simpleName} is missing @ProtoId annotation")
                ann.id
            }
        assertEquals(
            ids.size,
            ids.toSet().size,
            "Duplicate @ProtoId values on ${root.simpleName} subclasses")
        assertEquals(
            (ids.indices).toList(),
            ids.sorted(),
            "@ProtoId values on ${root.simpleName} must cover 0..N-1")
    }
}
