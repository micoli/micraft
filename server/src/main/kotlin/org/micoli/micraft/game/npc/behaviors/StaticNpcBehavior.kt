package org.micoli.micraft.game.npc.behaviors

import org.micoli.micraft.game.npc.NpcBehavior
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcPhysics
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.world.WorldState

class StaticNpcBehavior : NpcBehavior {
    override fun tick(instance: NpcInstance, world: WorldState, ctx: NpcTickContext): Boolean =
        NpcPhysics.applyGravity(instance, world)
}
