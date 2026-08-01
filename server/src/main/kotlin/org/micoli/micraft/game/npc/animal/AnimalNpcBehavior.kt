package org.micoli.micraft.game.npc.animal

import org.micoli.micraft.game.npc.NpcBehavior
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.world.WorldState

class AnimalNpcBehavior : NpcBehavior {
    private val movable = RandomMovableNpcBehavior()

    override fun tick(instance: NpcInstance, world: WorldState, ctx: NpcTickContext): Boolean {
        val animal = instance.animalData
        if (animal != null && instance.chaseTargetPos == null) {
            instance.chaseTargetPos = animal.preyTargetPos ?: animal.mateTargetPos
        }
        return movable.tick(instance, world, ctx)
    }
}
