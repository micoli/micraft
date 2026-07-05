package org.micoli.micraft.npc

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.micoli.micraft.npc.behaviors.InteractionableNpcBehavior
import org.micoli.micraft.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.npc.behaviors.StaticNpcBehavior

class NpcBehaviorRegistryTest {
    @Test
    fun getStatic_returnsStaticBehavior() {
        assertTrue(NpcBehaviorRegistry.get("static") is StaticNpcBehavior)
    }

    @Test
    fun getRandomMovable_returnsRandomMovableBehavior() {
        assertTrue(NpcBehaviorRegistry.get("random_movable") is RandomMovableNpcBehavior)
    }

    @Test
    fun getInteractionable_returnsInteractionableBehavior() {
        assertTrue(NpcBehaviorRegistry.get("interactionable") is InteractionableNpcBehavior)
    }

    @Test
    fun getUnknown_throwsError() {
        assertFailsWith<IllegalStateException> {
            NpcBehaviorRegistry.get("totally_unknown_behavior")
        }
    }

    @Test
    fun keys_containsBuiltinBehaviors() {
        val keys = NpcBehaviorRegistry.keys()
        assertTrue("static" in keys)
        assertTrue("random_movable" in keys)
        assertTrue("interactionable" in keys)
    }

    @Test
    fun register_customBehavior_retrievable() {
        val custom = StaticNpcBehavior()
        NpcBehaviorRegistry.register("test_npc_registry_custom", custom)
        val result = NpcBehaviorRegistry.get("test_npc_registry_custom")
        assertNotNull(result)
        assertTrue(result is StaticNpcBehavior)
    }
}
