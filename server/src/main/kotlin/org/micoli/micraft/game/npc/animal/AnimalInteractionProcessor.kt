package org.micoli.micraft.game.npc.animal

import kotlin.random.Random
import org.micoli.micraft.game.GameTimeService
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.FantasyNameGenerator
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
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
) {
    private var slowTickCounter = 0
    private val hpRegenAccumulators = mutableMapOf<String, Float>()

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
            animal.hunger = (animal.hunger + config.hungerRatePerDay * dtDays).coerceAtMost(1.0)

            tickHpRegen(instance, animal, config, dt.toFloat(), now)

            if (config.lifespanDays != null && animal.ageGameDays >= config.lifespanDays) {
                log.debug(
                    "NPC {} died of old age (age={:.1f})", instance.state.name, animal.ageGameDays)
                npcManager.killNpcByAge(instance.state.id, instance, now)
                continue
            }

            if (config.adultType != null &&
                animal.motherLevel > 0 &&
                instance.instanceLevel >= animal.motherLevel) {
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

    private fun tickHpRegen(
        instance: NpcInstance,
        animal: AnimalInstanceData,
        config: AnimalYamlEntry,
        dt: Float,
        now: Long
    ) {
        val combatExitMs = (config.combatExitDelaySec * 1000).toLong()
        val inCombat = now - instance.lastDamagedAtMs < combatExitMs

        if (config.hpRegenPerSec > 0f && !inCombat) {
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

    private suspend fun slowTick(currentDay: Double) {
        val allAnimal = npcManager.getAll().filter { !it.isDead && it.animalData != null }

        updateTargets(allAnimal, currentDay)
        tickPredation(allAnimal)
        tickHerbivoreFeeding(allAnimal)
        tickMating(allAnimal, currentDay)
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
            }

            if (config.canReproduce &&
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

            var found = false
            outer@ for (dy in -1..2) {
                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        val bx = cx + dx
                        val by = cy + dy
                        val bz = cz + dz
                        val block = world.getBlockIfLoaded(bx, by, bz)
                        if (block.id in FOOD_BLOCK_TYPES) {
                            val bxf = bx.toFloat() + 0.5f
                            val bzf = bz.toFloat() + 0.5f
                            val fdx = bxf - pos.x
                            val fdz = bzf - pos.z
                            if (fdx * fdx + fdz * fdz <= EAT_RANGE_SQ) {
                                consumeVegetation(bx, by, bz, block, animal, config)
                            } else {
                                animal.preyTargetPos =
                                    org.micoli.micraft.player.Vec3(bxf, pos.y, bzf)
                            }
                            found = true
                            break@outer
                        }
                    }
                }
            }
            if (!found) {
                animal.preyTargetPos = null
            }
        }
    }

    private suspend fun consumeVegetation(
        bx: Int,
        by: Int,
        bz: Int,
        block: BlockType,
        animal: AnimalInstanceData,
        config: AnimalYamlEntry
    ) {
        val blockPos = org.micoli.micraft.game.world.BlockPos(bx, by, bz)
        val change = BlockChange(blockPos, BlockType.AIR)
        world.applyChange(change)
        broadcast(ServerMessage.WorldUpdate(listOf(change)))
        vegetationManager.deactivate(blockPos)
        vegetationManager.tryActivate(blockPos, block)
        animal.hunger = (animal.hunger - config.feedHungerReduction).coerceAtLeast(0.0)
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

            if (distSq <= MATING_RANGE_SQ) {
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
        val count = Random.nextInt(config.offspringMinCount, config.offspringMaxCount + 1)
        val zoneLevel = world.zoneLevelAt(mother.state.pos.x.toInt(), mother.state.pos.z.toInt())

        val mateInstance = fatherAnimal.mateTargetId?.let { npcManager.getInstance(it) }
        val fatherId = mateInstance?.state?.id ?: mother.state.id

        val avgLevel =
            ((mother.instanceLevel + (mateInstance?.instanceLevel ?: mother.instanceLevel)) / 2 - 5)
                .coerceAtLeast(1)
        val offspringLevel = avgLevel.coerceAtMost(zoneLevel)

        repeat(count) {
            val offspringAnimal =
                AnimalInstanceData.offspring(
                    parentA = motherAnimal,
                    parentB = mateInstance?.animalData ?: motherAnimal,
                    statsVariance = config.statsVariance,
                    parentAId = mother.state.id,
                    parentBId = fatherId,
                    motherLevel = mother.instanceLevel,
                )
            val name =
                "${offspringType.replace('_', ' ').replaceFirstChar { it.uppercase() }} - ${FantasyNameGenerator.generate(offspringType)}"
            val offset =
                org.micoli.micraft.player.Vec3(
                    mother.state.pos.x + Random.nextFloat() * 2f - 1f,
                    mother.state.pos.y,
                    mother.state.pos.z + Random.nextFloat() * 2f - 1f,
                )
            val spawned = npcManager.spawnNpc(name, offspringType, offset, offspringLevel)
            spawned.animalData = offspringAnimal
            spawned.state = spawned.state.copy(animalData = offspringAnimal.toState())
            log.debug("Spawned offspring {} lv{}", name, offspringLevel)
        }
    }

    fun clearAccumulator(npcId: String) {
        hpRegenAccumulators.remove(npcId)
    }

    companion object {
        private const val SLOW_TICK_INTERVAL = 20
        private const val MATING_RANGE_SQ = 3.0f * 3.0f
        private const val EAT_RANGE_SQ = 2.5f * 2.5f
    }
}
