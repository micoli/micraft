package org.micoli.micraft.game.npc.pack

import java.util.concurrent.ConcurrentLinkedQueue
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(PackCoordinator::class.java)

/**
 * Membership and targeting for pack hunts. A wolf on its own cannot bring down a polar bear, so it
 * calls its kin from neighbour to neighbour and the pack only engages once enough of them have
 * gathered around the quarry.
 *
 * This class decides *who* hunts *what*; moving and hitting stay where they already are —
 * `RandomMovableNpcBehavior` follows `chaseTargetPos`, and `NpcManager.tickAggro` resolves
 * `npcAggroTarget` into attacks.
 */
class PackCoordinator(
    private val npcManager: NpcManager,
    private val broadcastCombatLog: suspend (String) -> Unit = {},
    private val onEvent: (PackEvent) -> Unit = {},
) {
    private val packs = LinkedHashMap<String, Pack>()
    private var packSeq = 0L

    /**
     * Retaliation calls raised from `applyDamage`, which is deep inside combat resolution. Queued
     * and drained on the next tick so pack bookkeeping stays in one place.
     */
    private val pendingRetaliations = ConcurrentLinkedQueue<Pair<String, String>>()

    fun activePacks(): Collection<Pack> = packs.values.toList()

    fun packOf(npcId: String): Pack? = packs.values.firstOrNull { npcId in it.memberIds }

    /** Hook for [NpcManager.applyDamage]: a wounded animal howls for its pack. */
    fun onNpcDamagedByNpc(victim: NpcInstance, attacker: NpcInstance) {
        val config = victim.definition.packConfig ?: return
        if (attacker.state.type !in config.hostileTypes) return
        if (victim.packId != null) return
        pendingRetaliations.add(victim.state.id to attacker.state.id)
    }

    suspend fun tick(now: Long = System.currentTimeMillis()) {
        val alive = npcManager.getAll().filter { !it.isDead }.sortedBy { it.state.id }
        drainRetaliations(alive, now)
        detect(alive, now)
        updatePacks(now)
    }

    // ── Formation ─────────────────────────────────────────────────────────────

    private suspend fun drainRetaliations(alive: List<NpcInstance>, now: Long) {
        while (true) {
            val (victimId, attackerId) = pendingRetaliations.poll() ?: return
            val victim = npcManager.getInstance(victimId) ?: continue
            val attacker = npcManager.getInstance(attackerId) ?: continue
            if (victim.isDead || attacker.isDead || victim.packId != null) continue
            val config = victim.definition.packConfig ?: continue
            formPack(victim, attacker, config, alive, now)
        }
    }

    /**
     * The NPC closest to a quarry raises the call, not whichever one the map happens to be iterated
     * into first — otherwise which wolf leads (and therefore who ends up in the pack) would depend
     * on UUID ordering rather than on where the animals actually stand.
     */
    private suspend fun detect(alive: List<NpcInstance>, now: Long) {
        val callers =
            alive
                .mapNotNull { instance ->
                    val config = instance.definition.packConfig ?: return@mapNotNull null
                    if (config.hostileTypes.isEmpty()) return@mapNotNull null
                    if (isSilenced(instance, config, now)) return@mapNotNull null
                    val target = nearestHostile(instance, config, alive) ?: return@mapNotNull null
                    Triple(instance, target, distSq(instance, target))
                }
                .sortedWith(compareBy({ it.third }, { it.first.state.id }))

        for ((instance, target, _) in callers) {
            if (instance.packId != null) continue
            val config = instance.definition.packConfig ?: continue
            formPack(instance, target, config, alive, now)
        }
    }

    /**
     * A hungry carnivore looks for a fight further out than it normally sees — that is the hunger
     * trigger, on top of the opportunistic one.
     */
    private fun detectionRadius(instance: NpcInstance): Float {
        val base = instance.definition.aggroRange
        val animalConfig = instance.definition.animalConfig ?: return base
        val animal = instance.animalData ?: return base
        if (animal.hunger < animalConfig.hungerThresholdToHunt) return base
        return maxOf(base, animalConfig.foodSearchRadius)
    }

    private fun nearestHostile(
        instance: NpcInstance,
        config: PackConfig,
        alive: List<NpcInstance>,
    ): NpcInstance? {
        val radius = detectionRadius(instance)
        val radiusSq = radius * radius
        return alive
            .filter { candidate ->
                candidate.state.type in config.hostileTypes &&
                    candidate.state.id != instance.state.id &&
                    Math.abs(candidate.state.pos.y - instance.state.pos.y) <= 5f &&
                    distSq(candidate, instance) <= radiusSq
            }
            .minWithOrNull(compareBy({ distSq(it, instance) }, { it.state.id }))
    }

    private suspend fun formPack(
        initiator: NpcInstance,
        target: NpcInstance,
        config: PackConfig,
        alive: List<NpcInstance>,
        now: Long,
    ) {
        packSeq++
        val pack =
            Pack(
                id = "pack-$packSeq",
                initiatorId = initiator.state.id,
                targetId = target.state.id,
                config = config,
                memberIds = linkedSetOf(),
                createdAtMs = now,
            )
        packs[pack.id] = pack
        join(pack, initiator, target)
        emit(PackEventType.PACK_CALL, pack, initiator, target)
        broadcastCombatLog(
            "[m:${initiator.state.name}] howls for the pack against [m:${target.state.name}]!")
        recruit(pack, initiator, target, alive, now)
        log.debug(
            "Pack {} formed by {} against {} ({} members)",
            pack.id,
            initiator.state.name,
            target.state.name,
            pack.memberIds.size)
    }

    /**
     * Neighbour-to-neighbour propagation: the call hops from each fresh recruit to *its* own
     * neighbours, so a wolf out of earshot of the initiator can still be reached through a relay.
     */
    private fun recruit(
        pack: Pack,
        initiator: NpcInstance,
        target: NpcInstance,
        alive: List<NpcInstance>,
        now: Long,
    ) {
        var frontier = listOf(initiator)
        var hops = 0
        while (hops < pack.config.relayHops &&
            pack.memberIds.size < pack.config.maxSize &&
            frontier.isNotEmpty()) {
            val next = mutableListOf<NpcInstance>()
            for (relay in frontier) {
                val relayConfig = relay.definition.packConfig ?: continue
                val allied = relayConfig.alliedTypes(relay.state.type)
                val radiusSq = relayConfig.callRadius * relayConfig.callRadius
                alive
                    .filter { candidate ->
                        val candidateConfig = candidate.definition.packConfig
                        candidate.packId == null &&
                            candidate.state.id !in pack.memberIds &&
                            candidate.state.id != target.state.id &&
                            candidate.state.type in allied &&
                            candidateConfig != null &&
                            !isSilenced(candidate, candidateConfig, now) &&
                            distSq(candidate, relay) <= radiusSq
                    }
                    .sortedWith(compareBy({ distSq(it, relay) }, { it.state.id }))
                    .forEach { candidate ->
                        if (pack.memberIds.size >= pack.config.maxSize) return@forEach
                        join(pack, candidate, target)
                        emit(PackEventType.PACK_JOIN, pack, candidate, target)
                        next += candidate
                    }
            }
            frontier = next
            hops++
        }
    }

    private fun join(pack: Pack, member: NpcInstance, target: NpcInstance) {
        pack.memberIds += member.state.id
        member.packId = pack.id
        member.packRallyPos = target.state.pos
        // A pack hunt cancels solo hunting and courtship.
        member.animalData?.let { animal ->
            animal.preyTargetId = null
            animal.preyTargetPos = null
            animal.mateTargetId = null
            animal.mateTargetPos = null
        }
    }

    // ── Rally, engagement, disband ────────────────────────────────────────────

    private suspend fun updatePacks(now: Long) {
        for (pack in packs.values.toList()) {
            pack.memberIds.removeAll { id ->
                val member = npcManager.getInstance(id)
                member == null || member.isDead
            }
            val target = npcManager.getInstance(pack.targetId)
            if (target == null || target.isDead || pack.memberIds.isEmpty()) {
                disband(pack, now)
                continue
            }
            val members = pack.memberIds.mapNotNull { npcManager.getInstance(it) }
            val gathered =
                members.count { member ->
                    val reach = member.definition.aggroRange
                    distSq(member, target) <= reach * reach
                }

            if (!pack.engaged) {
                if (gathered >= pack.config.minSizeToEngage) {
                    pack.engaged = true
                    val initiator = npcManager.getInstance(pack.initiatorId) ?: members.first()
                    emit(PackEventType.PACK_ENGAGE, pack, initiator, target)
                    broadcastCombatLog(
                        "The pack closes in on [m:${target.state.name}] (${members.size} strong)!")
                } else if (now - pack.createdAtMs > pack.config.rallyTimeoutSec * 1000) {
                    disband(pack, now)
                    continue
                }
            }

            members.forEach { member ->
                member.packRallyPos = target.state.pos
                // Before the quorum they only converge; no blows are struck.
                if (pack.engaged) member.npcAggroTarget = pack.targetId
            }
        }
    }

    private fun disband(pack: Pack, now: Long) {
        val members = pack.memberIds.mapNotNull { npcManager.getInstance(it) }
        members.forEach { member ->
            member.packId = null
            member.packRallyPos = null
            member.lastPackCallMs = now
            if (member.npcAggroTarget == pack.targetId) member.npcAggroTarget = null
        }
        packs.remove(pack.id)
        val reporter = npcManager.getInstance(pack.initiatorId) ?: members.firstOrNull()
        if (reporter != null) {
            emit(
                PackEventType.PACK_DISBAND,
                pack,
                reporter,
                npcManager.getInstance(pack.targetId),
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isSilenced(instance: NpcInstance, config: PackConfig, now: Long): Boolean =
        instance.lastPackCallMs > 0L &&
            now - instance.lastPackCallMs < config.callCooldownSec * 1000

    private fun distSq(a: NpcInstance, b: NpcInstance): Float {
        val dx = a.state.pos.x - b.state.pos.x
        val dz = a.state.pos.z - b.state.pos.z
        return dx * dx + dz * dz
    }

    private fun emit(
        type: PackEventType,
        pack: Pack,
        member: NpcInstance,
        target: NpcInstance?,
    ) =
        onEvent(
            PackEvent(
                type = type,
                packId = pack.id,
                npcId = member.state.id,
                npcName = member.state.name,
                npcType = member.state.type,
                otherId = target?.state?.id,
                otherName = target?.state?.name,
                value = pack.memberIds.size.toDouble(),
            ))
}
