package org.micoli.micraft.game.npc.animal

import org.micoli.micraft.game.GameTimeService
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.FantasyNameGenerator
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.npc.NpcGender
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(AnimalInteractionProcessor::class.java)

private val FOOD_BLOCK_TYPES = setOf(BlockType.FLOWER.id, BlockType.WEED.id)

class AnimalInteractionProcessor(
    private val npcManager: NpcManager,
    private val combatProcessor: CombatProcessor,
    private val world: WorldState,
    private val vegetationManager: VegetationManager,
    private val gameTimeService: GameTimeService,
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val ctxOf: () -> NpcTickContext = { NpcTickContext.live },
    /**
     * Veto on creating new life. Reproduction is exponential; a bounded world (the admin simulator)
     * needs to refuse births past its ceiling. Always true on the live server.
     */
    private val canSpawn: () -> Boolean = { true },
    /** Lifecycle sink; no-op on the live server, wired to the event log by the world simulator. */
    private val onEvent: (AnimalEvent) -> Unit = {},
) {
    private var slowTickCounter = 0
    private val hpRegenAccumulators = mutableMapOf<String, Float>()

    private fun emit(
        type: AnimalEventType,
        instance: NpcInstance,
        other: NpcInstance? = null,
        value: Double? = null,
    ) =
        onEvent(
            AnimalEvent(
                type = type,
                npcId = instance.state.id,
                npcName = instance.state.name,
                npcType = instance.state.type,
                otherId = other?.state?.id,
                otherName = other?.state?.name,
                value = value,
            ))

    suspend fun tick() {
        val dt = TICK_SECONDS.toDouble()
        val dtDays = dt / gameTimeService.gameDayDurationSeconds
        val currentDay = gameTimeService.currentGameDay
        val now = System.currentTimeMillis()

        for (instance in npcManager.getAll().toList()) {
            if (instance.isDead) continue
            val animal = instance.animalData ?: continue
            val config = instance.definition.animalConfig ?: continue

            animal.ageGameDays += dtDays
            val hungerBefore = animal.hunger
            animal.hunger = (animal.hunger + config.hungerRatePerDay * dtDays).coerceAtMost(1.0)
            if (hungerBefore < config.hungerThresholdToHunt &&
                animal.hunger >= config.hungerThresholdToHunt) {
                emit(AnimalEventType.HUNGRY, instance, value = animal.hunger)
            }

            val starving = animal.hunger >= config.starvationThreshold
            animal.starvingDays = if (starving) animal.starvingDays + dtDays else 0.0
            applyConditionMultipliers(instance, animal, config, starving)

            tickHpRegen(instance, animal, config, dt.toFloat(), now)

            if (starving &&
                config.starvationDeathDays > 0.0 &&
                animal.starvingDays >= config.starvationDeathDays) {
                log.debug(
                    "NPC {} starved to death after {} game days",
                    instance.state.name,
                    animal.starvingDays)
                npcManager.killNpcByStarvation(instance.state.id, instance, now)
                continue
            }

            if (config.lifespanDays != null && animal.ageGameDays >= config.lifespanDays) {
                log.debug(
                    "NPC {} died of old age (age={})", instance.state.name, animal.ageGameDays)
                // No AGE_DEATH event here: NpcManager reports every death, cause included. Emitting
                // one from both places is what made old-age deaths count twice in the metrics.
                npcManager.killNpcByAge(instance.state.id, instance, now)
                continue
            }

            // Two ways to grow up. The age timer is the one that carries a population: a baby that
            // has to earn a level first never gets there — it is passive and has no attacks — so
            // every birth used to end in an old-age death without a single adult being replaced.
            // The level branch is kept so an NPC that does gain levels still matures early.
            val grownByAge = config.growthDays != null && animal.ageGameDays >= config.growthDays
            val grownByLevel =
                animal.motherLevel > 0 && instance.instanceLevel >= animal.motherLevel
            if (config.adultType != null && (grownByAge || grownByLevel)) {
                emit(
                    AnimalEventType.EVOLVE,
                    instance,
                    value = instance.instanceLevel.toDouble(),
                )
                npcManager.evolveAnimal(instance, config.adultType, animal)
                continue
            }

            val gestation = animal.gestationRemainingDays
            if (gestation != null) {
                val newGestation = gestation - dtDays
                if (newGestation <= 0) {
                    animal.gestationRemainingDays = null
                    animal.lastReproductionDay = currentDay
                    spawnOffspring(instance, config, animal, currentDay)
                } else {
                    animal.gestationRemainingDays = newGestation
                }
            }
        }

        slowTickCounter++
        if (slowTickCounter >= SLOW_TICK_INTERVAL) {
            slowTickCounter = 0
            slowTick(currentDay)
        }
    }

    /**
     * The animal's condition, folded into the two multipliers every reader consumes.
     *
     * Multiplicative, so a starving pregnant female carries both penalties — which is the point:
     * her condition is worse than either one alone, and that is what makes her a reachable target
     * for a wolf pack that could not touch a healthy adult.
     */
    private fun applyConditionMultipliers(
        instance: NpcInstance,
        animal: AnimalInstanceData,
        config: AnimalYamlEntry,
        starving: Boolean,
    ) {
        var speed = 1f
        var damage = 1f
        if (starving) {
            speed *= config.starvationSpeedMultiplier
            damage *= config.starvationAttackMultiplier
        }
        if (animal.gestationRemainingDays != null) {
            speed *= config.gestationSpeedMultiplier
            damage *= config.gestationAttackMultiplier
        }
        instance.speedMultiplier = speed
        instance.damageMultiplier = damage
    }

    private fun tickHpRegen(
        instance: NpcInstance,
        animal: AnimalInstanceData,
        config: AnimalYamlEntry,
        dt: Float,
        now: Long
    ) {
        val combatExitMs = (config.combatExitDelaySec * 1000).toLong()
        val inCombat = now - instance.lastDamagedAtMs < combatExitMs

        // Regeneration is fed by food. Without this gate a hungry goat out of combat healed 60 hp a
        // minute while starvation took 3 — so no animal could ever actually weaken, and the whole
        // starvation mechanic would be dead code.
        val hungry = animal.hunger >= config.hungerThresholdToHunt

        if (config.hpRegenPerSec > 0f && !inCombat && !hungry) {
            val maxHp = instance.state.maxHp
            if (instance.currentHp < maxHp) {
                val acc = (hpRegenAccumulators[instance.state.id] ?: 0f) + config.hpRegenPerSec * dt
                val whole = acc.toInt()
                hpRegenAccumulators[instance.state.id] = acc - whole
                if (whole > 0) {
                    instance.currentHp = (instance.currentHp + whole).coerceAtMost(maxHp)
                    instance.state = instance.state.copy(currentHp = instance.currentHp)
                }
            }
        }

        if (config.manaRegenPerSec > 0f && !inCombat) {
            val maxMana = instance.maxMana
            if (maxMana > 0 && instance.currentMana < maxMana) {
                val id = "${instance.state.id}_mana"
                val acc = (hpRegenAccumulators[id] ?: 0f) + config.manaRegenPerSec * dt
                val whole = acc.toInt()
                hpRegenAccumulators[id] = acc - whole
                if (whole > 0) {
                    instance.currentMana = (instance.currentMana + whole).coerceAtMost(maxMana)
                }
            }
        }
    }

    /**
     * Which types hunt a given type, derived from every `preyTypes` list.
     *
     * Rebuilt only when the definition map is swapped (`/reload`), not per tick: it is a pure
     * function of the registry, and a prey animal needs it on every slow tick.
     */
    private var predatorsByPrey: Map<String, Set<String>> = emptyMap()
    private var predatorIndexSource: Map<String, org.micoli.micraft.game.npc.NpcDefinition>? = null

    private fun predatorsOf(type: String): Set<String> {
        val definitions = npcManager.getDefinitions()
        if (definitions !== predatorIndexSource) {
            val index = mutableMapOf<String, MutableSet<String>>()
            for ((predator, def) in definitions) {
                for (prey in def.animalConfig?.preyTypes.orEmpty()) {
                    index.getOrPut(prey) { mutableSetOf() }.add(predator)
                }
            }
            predatorsByPrey = index
            predatorIndexSource = definitions
        }
        return predatorsByPrey[type].orEmpty()
    }

    private suspend fun slowTick(currentDay: Double) {
        val allAnimal = npcManager.getAll().filter { !it.isDead && it.animalData != null }

        updateFlight(allAnimal)
        updateTargets(allAnimal, currentDay)
        tickPredation(allAnimal)
        tickHerbivoreFeeding(allAnimal)
        tickMating(allAnimal, currentDay)
    }

    /**
     * Point each threatened animal away from its nearest predator.
     *
     * The flight point is placed at `fleeRadius` in the opposite direction, so the animal keeps
     * running while the predator is close and stops as soon as the threat leaves — no timer to
     * tune.
     */
    private fun updateFlight(allAnimal: List<NpcInstance>) {
        for (instance in allAnimal) {
            val animal = instance.animalData ?: continue
            val config = instance.definition.animalConfig ?: continue
            if (config.fleeRadius <= 0f) {
                animal.fleeTargetPos = null
                continue
            }
            val predators = predatorsOf(instance.state.type)
            if (predators.isEmpty()) {
                animal.fleeTargetPos = null
                continue
            }

            val pos = instance.state.pos
            val fleeSq = config.fleeRadius * config.fleeRadius
            var nearest: NpcInstance? = null
            var nearestSq = Float.MAX_VALUE
            for (candidate in allAnimal) {
                if (candidate.state.type !in predators || candidate.isDead) continue
                val dx = candidate.state.pos.x - pos.x
                val dz = candidate.state.pos.z - pos.z
                val distSq = dx * dx + dz * dz
                if (distSq <= fleeSq && distSq < nearestSq) {
                    nearestSq = distSq
                    nearest = candidate
                }
            }

            val threat = nearest
            if (threat == null) {
                animal.fleeTargetPos = null
                continue
            }
            val dx = pos.x - threat.state.pos.x
            val dz = pos.z - threat.state.pos.z
            val dist = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
            // Standing exactly on the predator gives no direction to run in; anywhere is better
            // than
            // dividing by zero, so keep the previous point and let the next tick sort it out.
            if (dist < 0.01f) continue
            animal.fleeTargetPos =
                org.micoli.micraft.player.Vec3(
                    pos.x + dx / dist * config.fleeRadius,
                    pos.y,
                    pos.z + dz / dist * config.fleeRadius,
                )
        }
    }

    /**
     * Whether [instance] already has too many of its own kind nearby to breed.
     *
     * Counts the animal itself out. A herd hits its local limit and stops growing *there* while the
     * same species keeps breeding elsewhere — which is what a population regulated by its
     * surroundings looks like, as opposed to one held down by a world-wide number.
     */
    private fun isCrowded(
        instance: NpcInstance,
        config: AnimalYamlEntry,
        allAnimal: List<NpcInstance>,
    ): Boolean {
        if (config.maxLocalDensity <= 0) return false
        val pos = instance.state.pos
        val radiusSq = config.densityRadius * config.densityRadius
        var neighbours = 0
        for (candidate in allAnimal) {
            if (candidate.state.id == instance.state.id) continue
            if (candidate.state.type != instance.state.type || candidate.isDead) continue
            val dx = candidate.state.pos.x - pos.x
            val dz = candidate.state.pos.z - pos.z
            if (dx * dx + dz * dz > radiusSq) continue
            neighbours++
            if (neighbours >= config.maxLocalDensity) return true
        }
        return false
    }

    private fun updateTargets(allAnimal: List<NpcInstance>, currentDay: Double) {
        for (instance in allAnimal) {
            val animal = instance.animalData ?: continue
            val config = instance.definition.animalConfig ?: continue
            val pos = instance.state.pos

            val isCarnivore = config.diet == NpcDiet.CARNIVORE || config.diet == NpcDiet.OMNIVORE
            if (isCarnivore && animal.hunger >= config.hungerThresholdToHunt) {
                val existingPrey = animal.preyTargetId?.let { npcManager.getInstance(it) }
                if (existingPrey == null || existingPrey.isDead) {
                    val searchSq = config.foodSearchRadius * config.foodSearchRadius
                    val prey =
                        allAnimal.firstOrNull { candidate ->
                            candidate.state.id != instance.state.id &&
                                candidate.state.type in config.preyTypes &&
                                !candidate.isDead &&
                                run {
                                    val dx = candidate.state.pos.x - pos.x
                                    val dz = candidate.state.pos.z - pos.z
                                    dx * dx + dz * dz <= searchSq
                                }
                        }
                    animal.preyTargetId = prey?.state?.id
                    animal.preyTargetPos = prey?.state?.pos
                } else {
                    animal.preyTargetPos = existingPrey.state.pos
                }
            } else {
                animal.preyTargetId = null
                animal.preyTargetPos = null
                if (animal.hunger < config.hungerThresholdToHunt) animal.foodTargetPos = null
            }

            // Starvation sterility is stated separately from `hungerThresholdToMate` even though
            // the
            // default threshold already implies it: the two are independent knobs, and a tuning
            // pass
            // that raises the mating threshold must not quietly make starving animals fertile.
            val sterileFromHunger =
                config.starvationSterile && animal.hunger >= config.starvationThreshold
            val crowded = isCrowded(instance, config, allAnimal)
            if (config.canReproduce &&
                !sterileFromHunger &&
                !crowded &&
                animal.hunger < config.hungerThresholdToMate &&
                animal.gestationRemainingDays == null &&
                animal.mateTargetId == null) {
                val lastRepro = animal.lastReproductionDay
                if (lastRepro == null ||
                    currentDay - lastRepro >= config.reproductionCooldownDays) {
                    val rangeSq = config.matingRange * config.matingRange
                    val mate =
                        allAnimal.firstOrNull { candidate ->
                            val ca = candidate.animalData ?: return@firstOrNull false
                            candidate.state.id != instance.state.id &&
                                candidate.state.type == instance.state.type &&
                                !candidate.isDead &&
                                ca.gender != animal.gender &&
                                ca.gestationRemainingDays == null &&
                                ca.mateTargetId == null &&
                                !animal.parentIds.contains(candidate.state.id) &&
                                !ca.parentIds.contains(instance.state.id) &&
                                animal.parentIds.intersect(ca.parentIds).isEmpty() &&
                                run {
                                    val dx = candidate.state.pos.x - pos.x
                                    val dz = candidate.state.pos.z - pos.z
                                    dx * dx + dz * dz <= rangeSq
                                }
                        }
                    if (mate != null) {
                        animal.mateTargetId = mate.state.id
                        animal.mateTargetPos = mate.state.pos
                        mate.animalData!!.mateTargetId = instance.state.id
                        mate.animalData!!.mateTargetPos = instance.state.pos
                    }
                }
            }
        }
    }

    private suspend fun tickPredation(allAnimal: List<NpcInstance>) {
        for (instance in allAnimal) {
            val animal = instance.animalData ?: continue
            val preyId = animal.preyTargetId ?: continue
            val prey = npcManager.getInstance(preyId)
            if (prey == null || prey.isDead) {
                animal.preyTargetId = null
                animal.preyTargetPos = null
                continue
            }

            val config = instance.definition.animalConfig ?: continue
            val dx = prey.state.pos.x - instance.state.pos.x
            val dz = prey.state.pos.z - instance.state.pos.z
            val distSq = dx * dx + dz * dz
            val attackRange = instance.definition.aggroRange

            animal.preyTargetPos = prey.state.pos

            if (distSq <= attackRange * attackRange) {
                combatProcessor.handleNpcAttackNpc(instance, prey)
                if (prey.isDead) {
                    animal.hunger = (animal.hunger - config.feedHungerReduction).coerceAtLeast(0.0)
                    emit(AnimalEventType.FED, instance, other = prey, value = animal.hunger)
                    animal.preyTargetId = null
                    animal.preyTargetPos = null
                }
            }
        }
    }

    private suspend fun tickHerbivoreFeeding(allAnimal: List<NpcInstance>) {
        for (instance in allAnimal) {
            val animal = instance.animalData ?: continue
            val config = instance.definition.animalConfig ?: continue
            if (config.diet == NpcDiet.CARNIVORE) continue
            if (animal.hunger < config.hungerThresholdToHunt) continue
            if (animal.preyTargetId != null) continue

            val pos = instance.state.pos
            val radius = config.foodSearchRadius.toInt().coerceAtMost(10)
            val cx = pos.x.toInt()
            val cy = pos.y.toInt()
            val cz = pos.z.toInt()

            // Nearest plant, not the first one the scan happens to meet: picking by scan order made
            // the target jump between plants as the animal moved, so it never actually arrived.
            var bestX = 0
            var bestY = 0
            var bestZ = 0
            var bestBlock: BlockType? = null
            var bestDistSq = Float.MAX_VALUE
            for (dy in -1..2) {
                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        val bx = cx + dx
                        val by = cy + dy
                        val bz = cz + dz
                        val block = world.getBlockIfLoaded(bx, by, bz)
                        if (block.id !in FOOD_BLOCK_TYPES) continue
                        val fdx = bx.toFloat() + 0.5f - pos.x
                        val fdz = bz.toFloat() + 0.5f - pos.z
                        val distSq = fdx * fdx + fdz * fdz
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq
                            bestX = bx
                            bestY = by
                            bestZ = bz
                            bestBlock = block
                        }
                    }
                }
            }
            val target = bestBlock
            if (target == null) {
                animal.foodTargetPos = null
                continue
            }
            if (bestDistSq <= EAT_RANGE_SQ) {
                animal.foodTargetPos = null
                consumeVegetation(bestX, bestY, bestZ, target, instance, animal, config)
            } else {
                // Its own field, not `preyTargetPos`: a herbivore borrowing the predator's slot
                // only
                // worked because `updateTargets` happened to clear it first, and it made "walking
                // to
                // a meadow" indistinguishable from "hunting" everywhere else in the code.
                animal.foodTargetPos =
                    org.micoli.micraft.player.Vec3(
                        bestX.toFloat() + 0.5f, pos.y, bestZ.toFloat() + 0.5f)
            }
        }
    }

    private suspend fun consumeVegetation(
        bx: Int,
        by: Int,
        bz: Int,
        block: BlockType,
        instance: NpcInstance,
        animal: AnimalInstanceData,
        config: AnimalYamlEntry
    ) {
        val blockPos = org.micoli.micraft.game.world.BlockPos(bx, by, bz)
        val change = BlockChange(blockPos, BlockType.AIR)
        world.applyChange(change)
        broadcast(ServerMessage.WorldUpdate(listOf(change)))
        vegetationManager.deactivate(blockPos)
        vegetationManager.onGrazed(blockPos, block)
        animal.hunger = (animal.hunger - config.feedHungerReduction).coerceAtLeast(0.0)
        emit(AnimalEventType.FED, instance, value = animal.hunger)
        animal.preyTargetPos = null
    }

    private suspend fun tickMating(allAnimal: List<NpcInstance>, currentDay: Double) {
        for (instance in allAnimal) {
            val animal = instance.animalData ?: continue
            val mateId = animal.mateTargetId ?: continue
            val mate = npcManager.getInstance(mateId)
            if (mate == null || mate.isDead) {
                animal.mateTargetId = null
                animal.mateTargetPos = null
                continue
            }
            val mateAnimal = mate.animalData ?: continue

            val dx = mate.state.pos.x - instance.state.pos.x
            val dz = mate.state.pos.z - instance.state.pos.z
            val distSq = dx * dx + dz * dz

            mate.animalData?.mateTargetPos = mate.state.pos

            // Contact range, not the selection range: `matingRange` says how far a partner is worth
            // walking to (6–10 blocks), and using it here would have pairs conceiving across a
            // field.
            val contact =
                instance.definition.animalConfig?.matingContactRange ?: DEFAULT_MATING_CONTACT_RANGE
            if (distSq <= contact * contact) {
                val female =
                    if (animal.gender == NpcGender.FEMALE) Pair(instance, animal)
                    else Pair(mate, mateAnimal)
                val male =
                    if (animal.gender == NpcGender.MALE) Pair(instance, animal)
                    else Pair(mate, mateAnimal)
                val (_, femaleAnimal) = female
                val femaleConfig = female.first.definition.animalConfig
                if (femaleAnimal.gestationRemainingDays == null && femaleConfig != null) {
                    femaleAnimal.gestationRemainingDays = femaleConfig.gestationDays
                    femaleAnimal.lastReproductionDay = currentDay
                    male.second.lastReproductionDay = currentDay
                    log.debug("NPC {} and {} mating", instance.state.name, mate.state.name)
                    emit(AnimalEventType.MATING, instance, other = mate)
                    emit(
                        AnimalEventType.GESTATION_START,
                        female.first,
                        other = male.first,
                        value = femaleConfig.gestationDays)
                }
                animal.mateTargetId = null
                animal.mateTargetPos = null
                mateAnimal.mateTargetId = null
                mateAnimal.mateTargetPos = null
            }
        }
    }

    private suspend fun spawnOffspring(
        mother: NpcInstance,
        config: AnimalYamlEntry,
        motherAnimal: AnimalInstanceData,
        currentDay: Double
    ) {
        val fatherAnimal = mother.animalData ?: return
        val offspringType = config.offspringType ?: return
        val random = ctxOf().random
        val count = random.nextInt(config.offspringMinCount, config.offspringMaxCount + 1)
        val zoneLevel = world.zoneLevelAt(mother.state.pos.x.toInt(), mother.state.pos.z.toInt())

        val mateInstance = fatherAnimal.mateTargetId?.let { npcManager.getInstance(it) }
        val fatherId = mateInstance?.state?.id ?: mother.state.id

        val avgLevel =
            ((mother.instanceLevel + (mateInstance?.instanceLevel ?: mother.instanceLevel)) / 2 - 5)
                .coerceAtLeast(1)
        val offspringLevel = avgLevel.coerceAtMost(zoneLevel)

        repeat(count) {
            if (!canSpawn()) {
                // Counted, not silently dropped: a gestation that produced nothing because the
                // world
                // was full is the signal that the population ceiling — and not the ecology — is
                // what
                // regulates the arena.
                log.debug("Offspring of {} refused: population ceiling reached", mother.state.name)
                emit(AnimalEventType.BIRTH_BLOCKED, mother)
                return@repeat
            }
            val offspringAnimal =
                AnimalInstanceData.offspring(
                    parentA = motherAnimal,
                    parentB = mateInstance?.animalData ?: motherAnimal,
                    statsVariance = config.statsVariance,
                    parentAId = mother.state.id,
                    parentBId = fatherId,
                    motherLevel = mother.instanceLevel,
                    random = random,
                )
            val name =
                "${offspringType.replace('_', ' ').replaceFirstChar { it.uppercase() }} - ${FantasyNameGenerator.generate(offspringType)}"
            val offset =
                org.micoli.micraft.player.Vec3(
                    mother.state.pos.x + random.nextFloat() * 2f - 1f,
                    mother.state.pos.y,
                    mother.state.pos.z + random.nextFloat() * 2f - 1f,
                )
            val spawned =
                npcManager.spawnNpc(
                    name, offspringType, offset, offspringLevel, animalData = offspringAnimal)
            log.debug("Spawned offspring {} lv{}", name, offspringLevel)
            emit(AnimalEventType.BIRTH, spawned, other = mother, value = offspringLevel.toDouble())
        }
    }

    fun clearAccumulator(npcId: String) {
        hpRegenAccumulators.remove(npcId)
    }

    companion object {
        private const val SLOW_TICK_INTERVAL = 20
        private const val DEFAULT_MATING_CONTACT_RANGE = 2.5f
        private const val EAT_RANGE_SQ = 2.5f * 2.5f
    }
}
