package org.micoli.micraft.game.combat

import kotlin.math.sqrt
import kotlin.random.Random
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.AttackLevelDefinition
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.ClassResource
import org.micoli.micraft.player.rpg.DerivedStats
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("CombatProcessor")

private fun rollDice(spec: String): Int {
    val parts = spec.lowercase().split("d")
    if (parts.size != 2) return 1
    val count = parts[0].toIntOrNull() ?: 1
    val sides = parts[1].toIntOrNull() ?: 4
    return (1..count).sumOf { Random.nextInt(1, sides + 1) }
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

class CombatProcessor(
    private val config: CombatConfigData,
    private val attackRegistry: Map<String, AttackDefinition>,
    private val armorRegistry: Map<String, ArmorDefinition>,
    private val classRegistry: Map<String, ClassDefinitionEntry>,
    private val npcManager: NpcManager,
    private val getSessions: () -> Collection<PlayerSession>,
    private val broadcastCombatLog: suspend (String) -> Unit,
    private val subscribeToChannel: suspend (PlayerSession, String) -> Unit,
    @Suppress("unused") private val i18n: I18nConfig,
    private val savePlayer: suspend (PlayerSession) -> Unit,
) {
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
        val charData =
            session.characterData
                ?: run {
                    session.send(ServerMessage.Notification("No character — use /createcharacter"))
                    return
                }
        val attackDef =
            attackRegistry[msg.attackId]
                ?: run {
                    log.warn("Unknown attackId '{}' from {}", msg.attackId, session.id.take(8))
                    session.send(ServerMessage.Notification("Unknown attack '${msg.attackId}'"))
                    return
                }
        val classDef = classRegistry[charData.characterClass.name]
        val unlockedAttacks =
            classDef
                ?.levels
                ?.filter { (classLevel, _) -> classLevel <= charData.level }
                ?.values
                ?.flatMap { it.attacks } ?: emptyList()
        if (classDef != null &&
            unlockedAttacks.isNotEmpty() &&
            unlockedAttacks.none { it.attack == msg.attackId && it.level == msg.attackLevel }) {
            session.send(
                ServerMessage.Notification(
                    "Your class cannot use ${msg.attackId} level ${msg.attackLevel}"))
            return
        }
        val levelDef =
            attackDef.levels[msg.attackLevel]
                ?: run {
                    session.send(
                        ServerMessage.Notification(
                            "Unknown level ${msg.attackLevel} for '${msg.attackId}'"))
                    return
                }
        val now = System.currentTimeMillis()
        val cooldownKey = "${msg.attackId}:${msg.attackLevel}"
        if (now < session.combatState.attackCooldownUntilMs) {
            session.send(ServerMessage.Notification("Attack on cooldown"))
            return
        }
        if (now < (session.combatState.attackCooldownsUntilMs[cooldownKey] ?: 0L)) {
            session.send(
                ServerMessage.Notification("${msg.attackId} (rank ${msg.attackLevel}) on cooldown"))
            return
        }

        val range = levelDef.rangeOverride ?: config.maxCombatRange
        if (msg.isNpc)
            attackNpc(session, msg, attackDef, levelDef, charData, range, now, cooldownKey)
        else attackPlayer(session, msg, attackDef, levelDef, charData, range, now, cooldownKey)
    }

    private suspend fun attackPlayer(
        session: PlayerSession,
        msg: ClientMessage.AttackTarget,
        attackDef: AttackDefinition,
        levelDef: AttackLevelDefinition,
        charData: CharacterData,
        range: Float,
        now: Long,
        cooldownKey: String,
    ) {
        val target =
            getSessions().find { it.id == msg.targetId }
                ?: run {
                    session.send(ServerMessage.Notification("Target not found"))
                    return
                }
        val pos = session.state.pos
        val tPos = target.state.pos
        if (distance3(pos.x, pos.y, pos.z, tPos.x, tPos.y, tPos.z) > range) {
            session.send(ServerMessage.Notification("Target out of range"))
            return
        }
        val targetChar = target.characterData ?: return

        val myArmors = session.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
        val myDerived = DerivedStatsCalculator.compute(charData, myArmors)
        val theirArmors = target.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
        val theirDerived = DerivedStatsCalculator.compute(targetChar, theirArmors)

        if (!deductResource(session, charData, levelDef)) {
            session.send(ServerMessage.Notification("Not enough resources"))
            return
        }

        val (hit, isCrit, damage) =
            resolveAttack(attackDef, levelDef, myDerived, theirDerived.armorClass)
        session.combatState =
            session.combatState.copy(
                attackCooldownUntilMs = now + levelDef.cooldownMs,
                attackCooldownsUntilMs =
                    session.combatState.attackCooldownsUntilMs +
                        (cooldownKey to now + levelDef.cooldownMs),
            )

        if (hit && !target.state.godMode) {
            var newTargetChar =
                targetChar.copy(currentHp = (targetChar.currentHp - damage).coerceAtLeast(0))
            if (targetChar.characterClass.classResource == ClassResource.RAGE) {
                newTargetChar =
                    newTargetChar.copy(
                        currentRage = (newTargetChar.currentRage + 20).coerceAtMost(config.maxRage))
            }
            target.characterData = newTargetChar
            applyStatusEffect(target, levelDef, now)
            broadcastHealthUpdate(target.id, false, newTargetChar.currentHp, theirDerived.maxHp)
            subscribeToChannel(target, "combat")
            if (newTargetChar.currentHp <= 0) handlePlayerDowned(target)
            sendStatusUpdate(target, newTargetChar, theirDerived)
        }

        val hitMsg = if (hit) "hits for $damage${if (isCrit) " [CRIT]" else ""}" else "misses"
        broadcastCombatLog(
            "[p:${charData.name}] → [p:${targetChar.name}] (${msg.attackId}): $hitMsg")

        sendStatusUpdate(session, session.characterData ?: charData, myDerived)
        session.send(buildTargetUpdate(session))
    }

    private suspend fun attackNpc(
        session: PlayerSession,
        msg: ClientMessage.AttackTarget,
        attackDef: AttackDefinition,
        levelDef: AttackLevelDefinition,
        charData: CharacterData,
        range: Float,
        now: Long,
        cooldownKey: String,
    ) {
        val npc =
            npcManager.getInstance(msg.targetId)
                ?: run {
                    session.send(ServerMessage.Notification("Target not found"))
                    return
                }
        val pos = session.state.pos
        if (distance3(pos.x, pos.y, pos.z, npc.state.pos.x, npc.state.pos.y, npc.state.pos.z) >
            range) {
            session.send(ServerMessage.Notification("Target out of range"))
            return
        }

        val myArmors = session.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
        val myDerived = DerivedStatsCalculator.compute(charData, myArmors)

        if (!deductResource(session, charData, levelDef)) {
            session.send(ServerMessage.Notification("Not enough resources"))
            return
        }

        val npcAc = 10
        val (hit, isCrit, damage) = resolveAttack(attackDef, levelDef, myDerived, npcAc)
        session.combatState =
            session.combatState.copy(
                attackCooldownUntilMs = now + levelDef.cooldownMs,
                attackCooldownsUntilMs =
                    session.combatState.attackCooldownsUntilMs +
                        (cooldownKey to now + levelDef.cooldownMs),
            )

        if (hit) {
            npcManager.applyDamage(msg.targetId, damage, session.id)
            npcManager.applyStatusEffect(msg.targetId, levelDef, now)
        }

        val hitMsg = if (hit) "hits for $damage${if (isCrit) " [CRIT]" else ""}" else "misses"
        broadcastCombatLog(
            "[p:${charData.name}] → [m:${npc.state.name}] (${msg.attackId}): $hitMsg")

        sendStatusUpdate(session, session.characterData ?: charData, myDerived)
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
        val slots =
            def.attacks.ifEmpty {
                return
            }

        data class Resolved(
            val slot: org.micoli.micraft.game.npc.NpcAttackSlot,
            val attackDef: org.micoli.micraft.combat.AttackDefinition,
            val levelDef: org.micoli.micraft.combat.AttackLevelDefinition,
        )

        val dx = target.state.pos.x - npc.state.pos.x
        val dy = target.state.pos.y - npc.state.pos.y
        val dz = target.state.pos.z - npc.state.pos.z
        val distSq = dx * dx + dy * dy + dz * dz

        val resolved =
            slots.shuffled().firstNotNullOfOrNull { slot ->
                val cooldownKey = "${slot.attackId}:${slot.level}"
                if (now < (npc.attackCooldownsUntilMs[cooldownKey] ?: 0L))
                    return@firstNotNullOfOrNull null
                val aDef = attackRegistry[slot.attackId] ?: return@firstNotNullOfOrNull null
                val lDef =
                    aDef.levels[slot.level]
                        ?: aDef.levels.entries.maxByOrNull { it.key }?.value
                        ?: return@firstNotNullOfOrNull null
                val range = lDef.rangeOverride ?: config.npcMaxAttackRange
                if (distSq > range * range) return@firstNotNullOfOrNull null
                Resolved(slot, aDef, lDef)
            } ?: return

        val (slot, _, levelDef) = resolved

        when (def.classResource) {
            ClassResource.MANA -> {
                if (def.maxMana > 0 && levelDef.manaCost > 0) {
                    if (npc.currentMana < levelDef.manaCost) return
                    npc.currentMana -= levelDef.manaCost
                }
            }
            ClassResource.RAGE -> {
                if (def.maxRage > 0 && levelDef.rageCost > 0) {
                    if (npc.currentRage < levelDef.rageCost) return
                    npc.currentRage -= levelDef.rageCost
                }
            }
        }

        npc.attackCooldownsUntilMs["${slot.attackId}:${slot.level}"] = now + levelDef.cooldownMs

        val targetChar = target.characterData ?: return
        val theirArmors = target.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
        val theirDerived = DerivedStatsCalculator.compute(targetChar, theirArmors)

        val npcModifier = levelDef.power
        val roll = Random.nextInt(1, 21)
        val isCrit = roll == 20
        val hit = isCrit || (roll + npcModifier) >= theirDerived.armorClass

        val damage: Int
        if (hit && !target.state.godMode) {
            val raw = rollDice(levelDef.weaponDice) + levelDef.power
            damage = if (isCrit) raw * 2 else raw
            var newTargetChar =
                targetChar.copy(currentHp = (targetChar.currentHp - damage).coerceAtLeast(0))
            if (targetChar.characterClass.classResource == ClassResource.RAGE) {
                newTargetChar =
                    newTargetChar.copy(
                        currentRage = (newTargetChar.currentRage + 20).coerceAtMost(config.maxRage))
            }
            target.characterData = newTargetChar
            broadcastHealthUpdate(target.id, false, newTargetChar.currentHp, theirDerived.maxHp)
            subscribeToChannel(target, "combat")
            if (newTargetChar.currentHp <= 0) handlePlayerDowned(target)
            target.send(
                makeStatusUpdate(
                    newTargetChar,
                    theirDerived,
                    target.state.stance,
                    target.combatState.attackCooldownUntilMs,
                    target.combatState.attackCooldownsUntilMs,
                ))
        } else {
            damage = 0
            target.send(
                makeStatusUpdate(
                    targetChar,
                    theirDerived,
                    target.state.stance,
                    target.combatState.attackCooldownUntilMs,
                    target.combatState.attackCooldownsUntilMs,
                ))
        }

        val hitMsg = if (hit) "hits for $damage${if (isCrit) " [CRIT]" else ""}" else "misses"
        broadcastCombatLog(
            "[m:${npc.state.name}] → [p:${targetChar.name}] (${slot.attackId}): $hitMsg")
    }

    // ── Downed / death ────────────────────────────────────────────────────────

    internal suspend fun handlePlayerDowned(session: PlayerSession) {
        session.combatState = session.combatState.copy(downingSuccesses = 0, downingFailures = 0)
        getSessions().forEach { it.send(ServerMessage.PlayerDowned(session.id)) }
        log.info("Player {} downed", session.state.name)
    }

    suspend fun tickDowningRolls(session: PlayerSession) {
        if (!session.isDowned) return
        if (Random.nextInt(1, 21) >= 10) {
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
        val derived =
            DerivedStatsCalculator.compute(
                updated, session.state.armors.mapNotNull { armorRegistry[it]?.statBonus })
        broadcastHealthUpdate(session.id, false, 1, derived.maxHp)
        broadcastCombatLog("[p:${charData.name}] stabilizes.")
        session.send(ServerMessage.Notification("You have stabilized!"))
    }

    private suspend fun triggerDeath(session: PlayerSession) {
        val charData = session.characterData ?: return
        val armors = session.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
        val derived = DerivedStatsCalculator.compute(charData, armors)
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
        session.combatState = session.combatState.copy(downingSuccesses = 0, downingFailures = 0)
        getSessions().forEach {
            it.send(ServerMessage.PlayerRespawned(session.id, respawnPos, newHp, newMana))
        }
        broadcastHealthUpdate(session.id, false, newHp, derived.maxHp)
        broadcastCombatLog("[p:${charData.name}] has died!")
        savePlayer(session)
        log.info("Player {} died, respawned at {}", session.state.name, respawnPos)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private data class AttackResult(val hit: Boolean, val isCrit: Boolean, val damage: Int)

    private fun resolveAttack(
        attackDef: AttackDefinition,
        levelDef: AttackLevelDefinition,
        myDerived: DerivedStats,
        targetAc: Int
    ): AttackResult {
        val modifier =
            when (attackDef.damageType) {
                DamageType.PHYSICAL -> myDerived.meleeDmg
                DamageType.POISON -> myDerived.rangedDmg
                else -> myDerived.spellDmg
            }
        val roll = Random.nextInt(1, 21)
        val isCrit = roll == 20
        val hit = isCrit || (roll + modifier) >= targetAc
        val damage =
            if (hit) {
                val raw = rollDice(levelDef.weaponDice) + levelDef.power + modifier
                if (isCrit) raw * 2 else raw
            } else 0
        return AttackResult(hit, isCrit, damage)
    }

    private fun deductResource(
        session: PlayerSession,
        charData: CharacterData,
        levelDef: AttackLevelDefinition
    ): Boolean {
        val resource = charData.characterClass.classResource
        return when {
            resource == ClassResource.MANA && levelDef.manaCost > 0 -> {
                if (charData.currentMana < levelDef.manaCost) false
                else {
                    session.characterData =
                        charData.copy(currentMana = charData.currentMana - levelDef.manaCost)
                    true
                }
            }
            resource == ClassResource.RAGE && levelDef.rageCost > 0 -> {
                if (charData.currentRage < levelDef.rageCost) false
                else {
                    session.characterData =
                        charData.copy(currentRage = charData.currentRage - levelDef.rageCost)
                    true
                }
            }
            else -> true
        }
    }

    private suspend fun applyStatusEffect(
        target: PlayerSession,
        levelDef: AttackLevelDefinition,
        now: Long
    ) {
        val effect = levelDef.statusEffect ?: return
        val durationSec = levelDef.durationSec ?: effect.durationSec
        val expiry = now + (durationSec * 1000).toLong()
        val idx =
            target.combatState.activeEffects.indexOfFirst { it.effect::class == effect::class }
        if (idx >= 0) target.combatState.activeEffects[idx] = ActiveStatusEffect(effect, expiry)
        else target.combatState.activeEffects.add(ActiveStatusEffect(effect, expiry))
        target.send(
            ServerMessage.StatusEffectUpdate(target.id, target.combatState.activeEffects.toList()))
    }

    private suspend fun broadcastHealthUpdate(
        entityId: String,
        isNpc: Boolean,
        currentHp: Int,
        maxHp: Int
    ) {
        getSessions().forEach {
            it.send(ServerMessage.HealthUpdate(entityId, isNpc, currentHp, maxHp))
        }
        if (!isNpc) {
            getSessions()
                .find { it.id == entityId }
                ?.let { s ->
                    val charData = s.characterData ?: return@let
                    val armors = s.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
                    val derived = DerivedStatsCalculator.compute(charData, armors)
                    s.send(
                        makeStatusUpdate(
                            charData,
                            derived,
                            s.state.stance,
                            s.combatState.attackCooldownUntilMs,
                            s.combatState.attackCooldownsUntilMs,
                        ))
                }
        }
    }

    private suspend fun sendStatusUpdate(
        session: PlayerSession,
        charData: CharacterData,
        derived: DerivedStats
    ) {
        session.send(
            makeStatusUpdate(
                charData,
                derived,
                session.state.stance,
                session.combatState.attackCooldownUntilMs,
                session.combatState.attackCooldownsUntilMs,
            ))
    }

    fun makeStatusUpdate(
        charData: CharacterData,
        derived: DerivedStats,
        stance: PlayerStance,
        cooldownUntilMs: Long,
        attackCooldownsUntilMs: Map<String, Long> = emptyMap(),
    ): ServerMessage.PlayerStatusUpdate {
        val isRage = charData.characterClass.classResource == ClassResource.RAGE
        val now = System.currentTimeMillis()
        return ServerMessage.PlayerStatusUpdate(
            currentHp = charData.currentHp,
            maxHp = derived.maxHp,
            currentMana = if (isRage) 0 else charData.currentMana,
            maxMana = if (isRage) 0 else derived.maxMana,
            currentRage = if (isRage) charData.currentRage else 0,
            maxRage = if (isRage) config.maxRage else 0,
            currentTokens = if (isRage) charData.currentTokens else 0,
            maxTokens = if (isRage) derived.maxTokens else 0,
            stance = stance,
            globalCooldownRemainingMs = (cooldownUntilMs - now).coerceAtLeast(0),
            attackCooldownsRemainingMs =
                attackCooldownsUntilMs
                    .mapValues { (_, until) -> (until - now).coerceAtLeast(0) }
                    .filter { (_, rem) -> rem > 0 },
        )
    }

    fun buildTargetUpdate(session: PlayerSession): ServerMessage.CombatTargetUpdate {
        val targetId =
            session.combatState.targetId
                ?: return ServerMessage.CombatTargetUpdate(null, null, 0, 0)

        val pos = session.state.pos
        return if (session.combatState.targetIsNpc) {
            val npc =
                npcManager.getInstance(targetId)
                    ?: return ServerMessage.CombatTargetUpdate(targetId, "Unknown", 0, 0)
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
            val armors = targetSession.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
            val derived = targetChar?.let { DerivedStatsCalculator.compute(it, armors) }
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
            val armors = totSession.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
            val derived = DerivedStatsCalculator.compute(totChar, armors)
            ServerMessage.TargetRef(totId, totChar.name, totChar.currentHp, derived.maxHp)
        }
    }
}
