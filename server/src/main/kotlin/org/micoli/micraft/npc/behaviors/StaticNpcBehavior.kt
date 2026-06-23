package org.micoli.micraft.npc.behaviors

import org.micoli.micraft.npc.NpcBehavior
import org.micoli.micraft.npc.NpcInstance
import org.micoli.micraft.npc.NpcPhysics
import org.micoli.micraft.world.WorldState

class StaticNpcBehavior : NpcBehavior {
    override fun tick(instance: NpcInstance, world: WorldState): Boolean =
        NpcPhysics.applyGravity(instance, world)
}
