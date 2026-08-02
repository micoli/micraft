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
            // Flight first: nothing an animal wants is worth being eaten for. Then food before a
            // mate — one too hungry to breed has nothing to gain from courtship anyway, and
            // `hungerThresholdToMate` already refuses it.
            val errand =
                animal.fleeTargetPos
                    ?: animal.preyTargetPos
                    ?: animal.foodTargetPos
                    ?: animal.mateTargetPos
            instance.chaseTargetPos = errand
            // An errand is allowed to leave the home range; aggro and pack keep their own leashes.
            instance.chaseLeash =
                if (errand != null) instance.definition.animalConfig?.roamRadius else null
        }
        return movable.tick(instance, world, ctx)
    }
}
