package org.micoli.micraft.game.combat

import kotlin.math.sqrt
import kotlin.random.Random
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.AttackRankDefinition
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.combat.DiceSpec
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.combat.isMagical
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.rpg.CharacterStats
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.social.FactionManager
import org.micoli.micraft.game.vehicle.VehicleManager
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.ClassResource
import org.micoli.micraft.player.rpg.DerivedStats
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(CombatProcessor::class.java)

private fun rollDice(spec: String, rollSource: Random): Int {
    val dice = DiceSpec.parse(spec)
    if (!dice.isValid) return 1
    return (1..dice.count).sumOf { rollSource.nextInt(1, dice.sides + 1) }
}

/**
 * [raw] scaled by the attacker's condition (starving, pregnant), floored at 1.
 *
 * Only on the two NPC-initiated paths. Player damage goes through `resolveAttack`, which never sees
 * this — an NPC condition must not leak into what a player hits for.
 */
private fun scaleNpcDamage(raw: Int, attacker: NpcInstance): Int {
    val scaled = (raw * attacker.damageMultiplier).toInt()
    return scaled.coerceAtLeast(1)
}

private fun distance3(
    x1: Float,
    y1: Float,
    z1: Float,
    x2: Float,
    y2: Float,
    z2: Float,
): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    val dz = z1 - z2
    return sqrt(dx * dx + dy * dy + dz * dz)
}

@Suppress("LongParameterList")
class CombatProcessor(
    @Volatile private var config: CombatConfigData,
    @Volatile private var attackRegistry: Map<String, AttackDefinition>,
    classRegistry: Map<String, ClassDefinitionEntry>,
    private val npcManager: NpcManager,
    private val vehicleManager: VehicleManager = VehicleManager { _ -> },
    private val placeableManager: PlaceableManager = PlaceableManager { _ -> },
    private val getSessions: () -> Collection<PlayerSession>,
    private val broadcastCombatLog: suspend (String) -> Unit,
    private val subscribeToChannel: suspend (PlayerSession, String) -> Unit,
    val i18n: I18nConfig,
    private val savePlayer: suspend (PlayerSession) -> Unit,
    private val onPlayerDownedByNpc: suspend (session: PlayerSession, killerNpcId: String) -> Unit =
        { _, _ ->
        },
    private val factionManager: FactionManager? = null,
    val characterStats: CharacterStats =
        CharacterStats(maxRage = config.maxRage, savePlayer = savePlayer),
    /** Every die this processor rolls comes from here — supplied per World (ADR-0012). */
    private val rollSource: Random = Random.Default,
) {
    private val abilityGate = AbilityGate(classRegistry, config.globalCooldownMs)

    // ── Target selection ──────────────────────────────────────────────────────

    suspend fun handleSetTarget(session: PlayerSession, msg: ClientMessage.SetCombatTarget) {
        session.combatState =
            session.combatState.copy(
                targetId = msg.targetId,
                targetIsNpc = msg.isNpc,
            )
        session.send(buildTargetUpdate(session))
    }

    // ── Player attack ─────────────────────────────────────────────────────────

    suspend fun handleAttack(session: PlayerSession, msg: ClientMessage.AttackTarget) {
        val attackDef = attackRegistry[msg.attackId]
        if (attackDef == null)
            log.warn("Unknown attackId '{}' from {}", msg.attackId, session.id.take(8))
        val verdict =
            abilityGate.check(
                session,
                AbilityKind.ATTACK,
                msg.attackId,
                msg.attackRank,
                attackDef?.ranks?.mapValues { (_, rank) ->
                    AbilityRank(rank.abilityCost(), rank.cooldownMs)
                })
        val use =
            when (verdict) {
                is AbilityVerdict.Cleared -> verdict.ability
                is AbilityVerdict.Refused -> {
                    session.send(
                        verdict.notification(
                            i18n, session, AbilityKind.ATTACK, msg.attackId, msg.attackRank))
                    return
                }
            }
        val charData = session.characterData ?: return
        val rankDef = attackDef?.ranks?.get(msg.attackRank) ?: return

        val range = rankDef.rangeOverride ?: config.maxCombatRange
        if (msg.isNpc) attackNpc(session, msg, attackDef, rankDef, charData, range, use)
        else attackPlayer(session, msg, attackDef, rankDef, charData, range, use)
    }

    private suspend fun notify(session: PlayerSession, key: String, vararg args: Any) =
        session.send(ServerMessage.Notification(i18n.t(session.state.language, key, *args)))

    private suspend fun attackPlayer(
        session: PlayerSession,
        msg: ClientMessage.AttackTarget,
        attackDef: AttackDefinition,
        rankDef: AttackRankDefinition,
        charData: CharacterData,
        range: Float,
        use: AbilityUse,
    ) {
        val target =
            getSessions().find { it.id == msg.targetId }
                ?: run {
                    notify(session, "combat:server:target_not_found")
                    return
                }
        if (factionManager != null &&
            !factionManager.friendlyFireEnabled() &&
            factionManager.sameFaction(session, target)) {
            notify(session, "faction:server:friendly_fire_blocked")
            return
        }
        val pos = session.state.pos
        val tPos = target.state.pos
        if (distance3(pos.x, pos.y, pos.z, tPos.x, tPos.y, tPos.z) > range) {
            notify(session, "combat:server:out_of_range")
            return
        }
        val targetChar = target.characterData ?: return

        abilityGate.commit(session, use)
        val now = System.currentTimeMillis()
        val myDerived = characterStats.derived(session, charData)
        val theirDerived = characterStats.derived(target, targetChar)

        val result =
            resolveAttack(attackDef, rankDef, myDerived, theirDerived.armorClass, theirDerived)

        if (result.outcome == HitOutcome.HIT && !target.state.godMode) {
            var newTargetChar =
                targetChar.copy(currentHp = (targetChar.currentHp - result.damage).coerceAtLeast(0))
            if (targetChar.characterClass.classResource == ClassResource.RAGE) {
                newTargetChar =
                    newTargetChar.copy(
                        currentRage = (newTargetChar.currentRage + 20).coerceAtMost(config.maxRage))
            }
            target.characterData = newTargetChar
            applyStatusEffect(target, rankDef, now)
            broadcastHealthUpdate(target.id, false, newTargetChar.currentHp, theirDerived.maxHp)
            subscribeToChannel(target, "combat")
            if (newTargetChar.currentHp <= 0) handlePlayerDowned(target)
            characterStats.sendStatus(target)
        }

        broadcastCombatLog(
            "[p:${charData.name}] → [p:${targetChar.name}] (${msg.attackId}): ${getHitMessage(result)}")

        characterStats.sendStatus(session)
        session.send(buildTargetUpdate(session))
    }

    private suspend fun attackNpc(
        session: PlayerSession,
        msg: ClientMessage.AttackTarget,
        attackDef: AttackDefinition,
        rankDef: AttackRankDefinition,
        charData: CharacterData,
        range: Float,
        use: AbilityUse,
    ) {
        val npc =
            npcManager.getInstance(msg.targetId)
                ?: run {
                    notify(session, "combat:server:target_not_found")
                    return
                }
        val pos = session.state.pos
        if (distance3(pos.x, pos.y, pos.z, npc.state.pos.x, npc.state.pos.y, npc.state.pos.z) >
            range) {
            notify(session, "combat:server:out_of_range")
            return
        }

        abilityGate.commit(session, use)
        val now = System.currentTimeMillis()
        val myDerived = characterStats.derived(session, charData)

        val npcAc = 10
        val result = resolveAttack(attackDef, rankDef, myDerived, npcAc)

        if (result.outcome == HitOutcome.HIT) {
            npcManager.applyDamage(msg.targetId, result.damage, session.id)
            npcManager.applyStatusEffect(msg.targetId, rankDef, now, session.id)
        }

        broadcastCombatLog(
            "[p:${charData.name}] → [m:${npc.state.name}] (${msg.attackId}): ${getHitMessage(result)}")

        characterStats.sendStatus(session)
        session.send(buildTargetUpdate(session))
    }

    // ── NPC-initiated attack ──────────────────────────────────────────────────

    suspend fun handleNpcAttack(npc: NpcInstance, target: PlayerSession) {
        val now = System.currentTimeMillis()
        if (npc.activeEffects.any {
            it.effect is StatusEffect.FrozenInTime && it.expiresAtMs > now
        })
            return
        val def = npc.definition
        val distSq = target.state.pos.distanceSquaredTo(npc.state.pos)
        val choice = pickNpcAttack(npc, now) { range -> distSq <= range * range } ?: return
        val rankDef = choice.rankDef

        when (def.characterClass.classResource) {
            ClassResource.MANA -> {
                if (npc.maxMana > 0 && rankDef.manaCost > 0) {
                    if (npc.currentMana < rankDef.manaCost) return
                    npc.currentMana -= rankDef.manaCost
                }
            }
            ClassResource.RAGE -> {
                if (npc.maxRage > 0 && rankDef.rageCost > 0) {
                    if (npc.currentRage < rankDef.rageCost) return
                    npc.currentRage -= rankDef.rageCost
                }
            }
        }

        npc.attackCooldownsUntilMs[choice.cooldownKey] = now + rankDef.cooldownMs

        val targetChar = target.characterData ?: return
        val theirDerived = characterStats.derived(target, targetChar)

        val npcModifier = rankDef.power
        val roll = rollSource.nextInt(1, 21)
        val isCrit = roll == 20
        val hit = isCrit || (roll + npcModifier) >= theirDerived.armorClass
        val damageType = attackRegistry[choice.attackId]?.damageType ?: DamageType.PHYSICAL
        val avoided = hit && rollAvoided(rollSource, damageType, theirDerived)

        val damage: Int
        if (hit && !avoided && !target.state.godMode) {
            val raw = rollDice(rankDef.weaponDice, rollSource) + rankDef.power
            // Condition multiplier, never below 1 damage on a hit: a starving predator hits weakly
            // but a landed blow that does nothing reads as a bug rather than as weakness.
            damage = scaleNpcDamage(if (isCrit) raw * 2 else raw, npc)
            var newTargetChar =
                targetChar.copy(currentHp = (targetChar.currentHp - damage).coerceAtLeast(0))
            if (targetChar.characterClass.classResource == ClassResource.RAGE) {
                newTargetChar =
                    newTargetChar.copy(
                        currentRage = (newTargetChar.currentRage + 20).coerceAtMost(config.maxRage))
            }
            target.characterData = newTargetChar
            applyStatusEffect(target, rankDef, now)
            broadcastHealthUpdate(target.id, false, newTargetChar.currentHp, theirDerived.maxHp)
            subscribeToChannel(target, "combat")
            if (newTargetChar.currentHp <= 0) {
                handlePlayerDowned(target)
                onPlayerDownedByNpc(target, npc.state.id)
            }
            characterStats.sendStatus(target)
        } else {
            damage = 0
            characterStats.sendStatus(target)
        }

        val outcome =
            when {
                avoided -> if (damageType.isMagical) HitOutcome.RESISTED else HitOutcome.DODGED
                hit -> HitOutcome.HIT
                else -> HitOutcome.MISS
            }
        broadcastCombatLog(
            "[m:${npc.state.name}] → [p:${targetChar.name}] (${choice.attackId}): ${getHitMessage(AttackResult(outcome, isCrit, damage))}")
    }

    // ── NPC vs NPC attack ─────────────────────────────────────────────────────

    suspend fun handleNpcAttackNpc(predator: NpcInstance, prey: NpcInstance) {
        val now = System.currentTimeMillis()
        if (predator.activeEffects.any {
            it.effect is StatusEffect.FrozenInTime && it.expiresAtMs > now
        })
            return
        val distSq = prey.state.pos.distanceSquaredXZTo(predator.state.pos)
        val choice = pickNpcAttack(predator, now) { range -> distSq <= range * range } ?: return
        val rankDef = choice.rankDef
        predator.attackCooldownsUntilMs[choice.cooldownKey] = now + rankDef.cooldownMs

        val preyAc = 10 + prey.instanceLevel / 2
        val roll = rollSource.nextInt(1, 21)
        val isCrit = roll == 20
        val modifier = rankDef.power
        val hit = isCrit || (roll + modifier) >= preyAc

        if (hit) {
            val raw = rollDice(rankDef.weaponDice, rollSource) + rankDef.power
            val damage = scaleNpcDamage(if (isCrit) raw * 2 else raw, predator)
            npcManager.applyDamage(prey.state.id, damage, predator.state.id)
            val hitMsg = "hits for $damage${if (isCrit) " [CRIT]" else ""}"
            broadcastCombatLog(
                "[m:${predator.state.name}] → [m:${prey.state.name}] (${choice.attackId}): $hitMsg")
        } else {
            broadcastCombatLog(
                "[m:${predator.state.name}] → [m:${prey.state.name}] (${choice.attackId}): misses")
        }
    }

    private data class NpcAttackChoice(
        val attackId: String,
        val rank: Int,
        val rankDef: AttackRankDefinition,
    ) {
        val cooldownKey: String
            get() = "$attackId:$rank"
    }

    /**
     * A random Attack of [npc] off cooldown, at the Rank of its current Level, whose range fits.
     */
    private fun pickNpcAttack(
        npc: NpcInstance,
        now: Long,
        inRange: (Float) -> Boolean,
    ): NpcAttackChoice? =
        npc.definition.attacks.shuffled(rollSource).firstNotNullOfOrNull { slot ->
            val attackDef = attackRegistry[slot.attackId] ?: return@firstNotNullOfOrNull null
            val rank = attackDef.usableRank(npc.instanceLevel) ?: return@firstNotNullOfOrNull null
            val choice = NpcAttackChoice(slot.attackId, rank, attackDef.ranks.getValue(rank))
            if (now < (npc.attackCooldownsUntilMs[choice.cooldownKey] ?: 0L))
                return@firstNotNullOfOrNull null
            val range = choice.rankDef.rangeOverride ?: config.npcMaxAttackRange
            choice.takeIf { inRange(range) }
        }

    // ── Downed / death ────────────────────────────────────────────────────────

    internal suspend fun handlePlayerDowned(session: PlayerSession) {
        session.combatState = session.combatState.copy(downingSuccesses = 0, downingFailures = 0)
        getSessions().forEach { it.send(ServerMessage.PlayerDowned(session.id)) }
        log.info("Player {} downed", session.state.name)
    }

    suspend fun tickDowningRolls(session: PlayerSession) {
        if (!session.isDowned) return
        if (rollSource.nextInt(1, 21) >= 10) {
            val s =
                session.combatState.copy(
                    downingSuccesses = session.combatState.downingSuccesses + 1)
            session.combatState = s
            if (s.downingSuccesses >= 3) stabilize(session)
        } else {
            val s =
                session.combatState.copy(downingFailures = session.combatState.downingFailures + 1)
            session.combatState = s
            if (s.downingFailures >= 3) triggerDeath(session)
        }
    }

    private suspend fun stabilize(session: PlayerSession) {
        val charData = session.characterData ?: return
        val updated = charData.copy(currentHp = 1)
        session.characterData = updated
        session.combatState = session.combatState.copy(downingSuccesses = 0, downingFailures = 0)
        val derived = characterStats.derived(session, updated)
        broadcastHealthUpdate(session.id, false, 1, derived.maxHp)
        broadcastCombatLog("[p:${charData.name}] stabilizes.")
        notify(session, "combat:server:stabilized")
    }

    private suspend fun triggerDeath(session: PlayerSession) {
        val charData = session.characterData ?: return
        session.combatState = session.combatState.copy(downingSuccesses = 0, downingFailures = 0)
        // A respawn starts clean — a Protection's uptime is a per-fight tactical choice, not a
        // permanent buff to carry into the next one. Cleared *before* computing derived stats
        // below, so a respawn's new HP/mana max never includes a Protection bonus that is about
        // to be removed.
        session.combatState.activeEffects.clear()
        session.send(ServerMessage.StatusEffectUpdate(session.id, emptyList()))

        val derived = characterStats.derived(session, charData)
        val newHp = (derived.maxHp / 2).coerceAtLeast(1)
        val newMana = (derived.maxMana / 2).coerceAtLeast(0)
        val xpLoss = (charData.xp * 0.1).toInt()
        val respawnPos = charData.restPoint.firstOrNull() ?: session.state.pos

        session.characterData =
            charData.copy(
                currentHp = newHp,
                currentMana = newMana,
                xp = (charData.xp - xpLoss).coerceAtLeast(0),
            )
        getSessions().forEach {
            it.send(ServerMessage.PlayerRespawned(session.id, respawnPos, newHp, newMana))
        }
        broadcastHealthUpdate(session.id, false, newHp, derived.maxHp)
        broadcastCombatLog("[p:${charData.name}] has died!")
        savePlayer(session)
        log.info("Player {} died, respawned at {}", session.state.name, respawnPos)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private enum class HitOutcome {
        MISS,
        HIT,
        DODGED,
        RESISTED
    }

    private data class AttackResult(val outcome: HitOutcome, val isCrit: Boolean, val damage: Int)

    /**
     * To-hit + damage roll. When [avoidanceDefender] is given (a Character target), a hit that
     * lands rolls Dodge or Magic resistance next (ADR-0013): an avoided hit skips the damage roll
     * entirely. NPC targets pass no [avoidanceDefender] and never avoid.
     */
    private fun resolveAttack(
        attackDef: AttackDefinition,
        rankDef: AttackRankDefinition,
        myDerived: DerivedStats,
        targetAc: Int,
        avoidanceDefender: DerivedStats? = null,
    ): AttackResult {
        val modifier =
            when (attackDef.damageType) {
                DamageType.PHYSICAL -> myDerived.meleeDmg
                DamageType.POISON -> myDerived.rangedDmg
                else -> myDerived.spellDmg
            }
        val roll = rollSource.nextInt(1, 21)
        val isCrit = roll == 20
        val hit = isCrit || (roll + modifier) >= targetAc
        if (!hit) return AttackResult(HitOutcome.MISS, false, 0)
        if (avoidanceDefender != null &&
            rollAvoided(rollSource, attackDef.damageType, avoidanceDefender)) {
            val outcome =
                if (attackDef.damageType.isMagical) HitOutcome.RESISTED else HitOutcome.DODGED
            return AttackResult(outcome, isCrit, 0)
        }
        val raw = rollDice(rankDef.weaponDice, rollSource) + rankDef.power + modifier
        val damage = if (isCrit) raw * 2 else raw
        return AttackResult(HitOutcome.HIT, isCrit, damage)
    }

    private suspend fun applyStatusEffect(
        target: PlayerSession,
        rankDef: AttackRankDefinition,
        now: Long
    ) {
        val effect = rankDef.statusEffect ?: return
        val durationSec = rankDef.durationSec ?: effect.durationSec
        applyStatusEffectTo(target, effect, durationSec, now)
    }

    suspend fun applyStatusEffectTo(
        target: PlayerSession,
        effect: StatusEffect,
        durationSec: Float,
        now: Long,
        protectionId: String? = null,
        rank: Int? = null,
    ) {
        val expiry = now + (durationSec * 1000).toLong()
        val active = ActiveStatusEffect(effect, expiry, protectionId, rank)
        val idx =
            target.combatState.activeEffects.indexOfFirst { it.effect::class == effect::class }
        if (idx >= 0) target.combatState.activeEffects[idx] = active
        else target.combatState.activeEffects.add(active)
        target.send(
            ServerMessage.StatusEffectUpdate(target.id, target.combatState.activeEffects.toList()))

        if (!characterStats.affectsStats(effect)) return
        val charData = target.characterData ?: return
        val derived = characterStats.derived(target, charData)
        val updated =
            when (effect) {
                is StatusEffect.HpBoost -> charData.copy(currentHp = derived.maxHp)
                is StatusEffect.ManaBoost -> charData.copy(currentMana = derived.maxMana)
                else -> charData
            }
        target.characterData = updated
        val healthUpdate =
            ServerMessage.HealthUpdate(target.id, false, updated.currentHp, derived.maxHp)
        getSessions().forEach { it.send(healthUpdate) }
        characterStats.resync(target)
    }

    /**
     * Adds or clears the [StatusEffect.Drowning] DoT on a session as its breath meter empties or
     * refills. Refreshed each tick while drowning so [StatusEffectProcessor] keeps it alive.
     */
    suspend fun updateDrowning(session: PlayerSession, drowning: Boolean) {
        val effects = session.combatState.activeEffects
        val has = effects.any { it.effect is StatusEffect.Drowning }
        if (drowning) {
            applyStatusEffectTo(
                session,
                StatusEffect.Drowning,
                StatusEffect.Drowning.durationSec,
                System.currentTimeMillis())
        } else if (has) {
            effects.removeAll { it.effect is StatusEffect.Drowning }
            session.send(ServerMessage.StatusEffectUpdate(session.id, effects.toList()))
        }
    }

    /**
     * Direct-damage path for a guaranteed hit with no to-hit roll — e.g. a siege projectile's AoE
     * blast (see [org.micoli.micraft.game.placeable.siege.SiegeProjectileManager]). Mirrors the
     * HP-mutation/broadcast/downed-check tail of [attackPlayer], minus target resolution, range
     * check, and [resolveAttack]'s roll.
     */
    suspend fun applyDirectDamage(target: PlayerSession, damage: Int, sourceLabel: String) {
        if (target.state.godMode) return
        val targetChar = target.characterData ?: return
        val theirDerived = characterStats.derived(target, targetChar)

        val newTargetChar =
            targetChar.copy(currentHp = (targetChar.currentHp - damage).coerceAtLeast(0))
        target.characterData = newTargetChar
        broadcastHealthUpdate(target.id, false, newTargetChar.currentHp, theirDerived.maxHp)
        subscribeToChannel(target, "combat")
        broadcastCombatLog(
            "[$sourceLabel] → [p:${targetChar.name}]: ${getHitMessage(AttackResult(HitOutcome.HIT, false, damage))}")
        if (newTargetChar.currentHp <= 0) handlePlayerDowned(target)
        characterStats.sendStatus(target)
    }

    /** NPC-target counterpart to [applyDirectDamage] — same guaranteed-hit, no-roll contract. */
    suspend fun applyDirectDamageToNpc(
        attackerId: String,
        npcId: String,
        damage: Int,
        sourceLabel: String
    ) {
        val npc = npcManager.getInstance(npcId) ?: return
        if (npc.isDead) return
        npcManager.applyDamage(npcId, damage, attackerId)
        broadcastCombatLog(
            "[$sourceLabel] → [m:${npc.state.name}]: ${getHitMessage(AttackResult(HitOutcome.HIT, false, damage))}")
    }

    internal suspend fun broadcastHealthUpdate(
        entityId: String,
        isNpc: Boolean,
        currentHp: Int,
        maxHp: Int
    ) {
        getSessions().forEach {
            it.send(ServerMessage.HealthUpdate(entityId, isNpc, currentHp, maxHp))
        }
        if (!isNpc) {
            getSessions().find { it.id == entityId }?.let { characterStats.sendStatus(it) }
        }
    }

    fun buildTargetUpdate(session: PlayerSession): ServerMessage.CombatTargetUpdate {
        val targetId =
            session.combatState.targetId
                ?: return ServerMessage.CombatTargetUpdate(null, null, 0, 0)

        val pos = session.state.pos
        return if (session.combatState.targetIsNpc) {
            val npc =
                npcManager.getInstance(targetId)
                    ?: run {
                        val vehicle = vehicleManager.get(targetId)
                        if (vehicle != null) {
                            val dist =
                                distance3(
                                    pos.x,
                                    pos.y,
                                    pos.z,
                                    vehicle.pos.x,
                                    vehicle.pos.y,
                                    vehicle.pos.z)
                            return ServerMessage.CombatTargetUpdate(
                                targetId, vehicle.type.id, 0, 0, distance = dist)
                        }
                        val placeable = placeableManager.get(targetId)
                        if (placeable != null) {
                            val dist =
                                distance3(
                                    pos.x,
                                    pos.y,
                                    pos.z,
                                    placeable.pos.x,
                                    placeable.pos.y,
                                    placeable.pos.z)
                            return ServerMessage.CombatTargetUpdate(
                                targetId, placeable.type.id, 0, 0, distance = dist)
                        }
                        return ServerMessage.CombatTargetUpdate(targetId, "Unknown", 0, 0)
                    }
            val dist =
                distance3(pos.x, pos.y, pos.z, npc.state.pos.x, npc.state.pos.y, npc.state.pos.z)
            ServerMessage.CombatTargetUpdate(
                targetId,
                npc.state.name,
                npc.state.currentHp,
                npc.state.maxHp,
                distance = dist,
                level = npc.instanceLevel)
        } else {
            val targetSession =
                getSessions().find { it.id == targetId }
                    ?: return ServerMessage.CombatTargetUpdate(targetId, "Unknown", 0, 0)
            val targetChar = targetSession.characterData
            val derived = targetChar?.let { characterStats.derived(targetSession, it) }
            val tot = buildTargetOfTarget(targetSession)
            val tPos = targetSession.state.pos
            val dist = distance3(pos.x, pos.y, pos.z, tPos.x, tPos.y, tPos.z)
            ServerMessage.CombatTargetUpdate(
                targetId = targetId,
                displayName = targetChar?.name ?: targetSession.state.name,
                currentHp = targetChar?.currentHp ?: 0,
                maxHp = derived?.maxHp ?: 0,
                targetOfTarget = tot,
                distance = dist,
            )
        }
    }

    private fun buildTargetOfTarget(session: PlayerSession): ServerMessage.TargetRef? {
        val totId = session.combatState.targetId ?: return null
        return if (session.combatState.targetIsNpc) {
            val npc = npcManager.getInstance(totId) ?: return null
            ServerMessage.TargetRef(totId, npc.state.name, npc.state.currentHp, npc.state.maxHp)
        } else {
            val totSession = getSessions().find { it.id == totId } ?: return null
            val totChar = totSession.characterData ?: return null
            val derived = characterStats.derived(totSession, totChar)
            ServerMessage.TargetRef(totId, totChar.name, totChar.currentHp, derived.maxHp)
        }
    }

    private fun getHitMessage(result: AttackResult): String =
        when (result.outcome) {
            HitOutcome.MISS -> "misses"
            HitOutcome.DODGED -> "dodged"
            HitOutcome.RESISTED -> "resisted"
            HitOutcome.HIT -> {
                val string = if (result.isCrit) " [CRIT]" else ""
                "hits for ${result.damage}$string"
            }
        }

    fun reload(
        config: CombatConfigData,
        attackRegistry: Map<String, AttackDefinition>,
        classRegistry: Map<String, ClassDefinitionEntry>,
        spellRegistry: Map<String, SpellDefinition> = emptyMap(),
    ) {
        this.config = config
        characterStats.reload(maxRage = config.maxRage, protectionSpells = spellRegistry)
        this.attackRegistry = attackRegistry
        abilityGate.reload(classRegistry, config.globalCooldownMs)
    }
}

private fun AttackRankDefinition.abilityCost() = AbilityCost(mana = manaCost, rage = rageCost)
