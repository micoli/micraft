package org.micoli.micraft.game.combat

import kotlin.math.sqrt
import kotlin.random.Random
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.game.armor.ArmorDefinition
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
        if (charData.characterClass !in attackDef.eligibleClasses) {
            session.send(ServerMessage.Notification("Your class cannot use this attack"))
            return
        }
        val now = System.currentTimeMillis()
        if (now < session.combatState.attackCooldownUntilMs) {
            session.send(ServerMessage.Notification("Attack on cooldown"))
            return
        }

        val range = attackDef.rangeOverride ?: config.maxCombatRange
        if (msg.isNpc) attackNpc(session, msg, attackDef, charData, range, now)
        else attackPlayer(session, msg, attackDef, charData, range, now)
    }

    private suspend fun attackPlayer(
        session: PlayerSession,
        msg: ClientMessage.AttackTarget,
        attackDef: AttackDefinition,
        charData: CharacterData,
        range: Float,
        now: Long,
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

        if (!deductResource(session, charData, attackDef)) {
            session.send(ServerMessage.Notification("Not enough resources"))
            return
        }

        val (hit, isCrit, damage) = resolveAttack(attackDef, myDerived, theirDerived.armorClass)
        session.combatState =
            session.combatState.copy(attackCooldownUntilMs = now + attackDef.cooldownMs)

        if (hit) {
            val newHp = (targetChar.currentHp - damage).coerceAtLeast(0)
            target.characterData = targetChar.copy(currentHp = newHp)
            applyStatusEffect(target, attackDef, now)
            broadcastHealthUpdate(target.id, false, newHp, theirDerived.maxHp)
            subscribeToChannel(target, "combat")
            if (newHp <= 0) handlePlayerDowned(target)
        }

        val hitMsg = if (hit) "hits for $damage${if (isCrit) " [CRIT]" else ""}" else "misses"
        broadcastCombatLog("${charData.name} → ${targetChar.name}: $hitMsg")

        sendStatusUpdate(session, session.characterData ?: charData, myDerived)
        session.send(buildTargetUpdate(session))
    }

    private suspend fun attackNpc(
        session: PlayerSession,
        msg: ClientMessage.AttackTarget,
        attackDef: AttackDefinition,
        charData: CharacterData,
        range: Float,
        now: Long,
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

        if (!deductResource(session, charData, attackDef)) {
            session.send(ServerMessage.Notification("Not enough resources"))
            return
        }

        val npcAc = 10
        val (hit, isCrit, damage) = resolveAttack(attackDef, myDerived, npcAc)
        session.combatState =
            session.combatState.copy(attackCooldownUntilMs = now + attackDef.cooldownMs)

        if (hit) {
            npcManager.applyDamage(msg.targetId, damage, session.id)
        }

        val hitMsg = if (hit) "hits for $damage${if (isCrit) " [CRIT]" else ""}" else "misses"
        broadcastCombatLog("${charData.name} → ${npc.state.name}: $hitMsg")

        sendStatusUpdate(session, session.characterData ?: charData, myDerived)
        session.send(buildTargetUpdate(session))
    }

    // ── NPC-initiated attack ──────────────────────────────────────────────────

    suspend fun handleNpcAttack(npc: NpcInstance, target: PlayerSession) {
        val attackId = npc.definition.attackId ?: "basic_attack"
        val attackDef = attackRegistry[attackId] ?: return
        val now = System.currentTimeMillis()
        if (now < npc.attackCooldownUntilMs) return
        npc.attackCooldownUntilMs = now + attackDef.cooldownMs

        val targetChar = target.characterData ?: return
        val theirArmors = target.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
        val theirDerived = DerivedStatsCalculator.compute(targetChar, theirArmors)

        val npcModifier = attackDef.power
        val roll = Random.nextInt(1, 21)
        val isCrit = roll == 20
        val hit = isCrit || (roll + npcModifier) >= theirDerived.armorClass

        val damage: Int
        if (hit) {
            val raw = rollDice(attackDef.weaponDice) + attackDef.power
            damage = if (isCrit) raw * 2 else raw
            val newHp = (targetChar.currentHp - damage).coerceAtLeast(0)
            target.characterData = targetChar.copy(currentHp = newHp)
            broadcastHealthUpdate(target.id, false, newHp, theirDerived.maxHp)
            subscribeToChannel(target, "combat")
            if (newHp <= 0) handlePlayerDowned(target)
        } else {
            damage = 0
        }

        val hitMsg = if (hit) "hits for $damage${if (isCrit) " [CRIT]" else ""}" else "misses"
        broadcastCombatLog("${npc.state.name} → ${targetChar.name}: $hitMsg")

        target.send(
            makeStatusUpdate(
                targetChar,
                theirDerived,
                target.state.stance,
                target.combatState.attackCooldownUntilMs))
    }

    // ── Downed / death ────────────────────────────────────────────────────────

    private suspend fun handlePlayerDowned(session: PlayerSession) {
        session.combatState =
            session.combatState.copy(isDowned = true, downingSuccesses = 0, downingFailures = 0)
        getSessions().forEach { it.send(ServerMessage.PlayerDowned(session.id)) }
        log.info("Player {} downed", session.state.name)
    }

    suspend fun tickDowningRolls(session: PlayerSession) {
        if (!session.combatState.isDowned) return
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
        session.combatState =
            session.combatState.copy(isDowned = false, downingSuccesses = 0, downingFailures = 0)
        val derived =
            DerivedStatsCalculator.compute(
                updated, session.state.armors.mapNotNull { armorRegistry[it]?.statBonus })
        broadcastHealthUpdate(session.id, false, 1, derived.maxHp)
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
        session.combatState =
            session.combatState.copy(isDowned = false, downingSuccesses = 0, downingFailures = 0)
        getSessions().forEach {
            it.send(ServerMessage.PlayerRespawned(session.id, respawnPos, newHp, newMana))
        }
        broadcastHealthUpdate(session.id, false, newHp, derived.maxHp)
        savePlayer(session)
        log.info("Player {} died, respawned at {}", session.state.name, respawnPos)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private data class AttackResult(val hit: Boolean, val isCrit: Boolean, val damage: Int)

    private fun resolveAttack(
        attackDef: AttackDefinition,
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
                val raw = rollDice(attackDef.weaponDice) + attackDef.power + modifier
                if (isCrit) raw * 2 else raw
            } else 0
        return AttackResult(hit, isCrit, damage)
    }

    private fun deductResource(
        session: PlayerSession,
        charData: CharacterData,
        attackDef: AttackDefinition
    ): Boolean {
        val resource = charData.characterClass.classResource
        return when {
            resource == ClassResource.MANA && attackDef.manaCost > 0 -> {
                if (charData.currentMana < attackDef.manaCost) false
                else {
                    session.characterData =
                        charData.copy(currentMana = charData.currentMana - attackDef.manaCost)
                    true
                }
            }
            resource == ClassResource.RAGE && attackDef.rageCost > 0 -> {
                if (charData.currentRage < attackDef.rageCost) false
                else {
                    session.characterData =
                        charData.copy(currentRage = charData.currentRage - attackDef.rageCost)
                    true
                }
            }
            else -> true
        }
    }

    private suspend fun applyStatusEffect(
        target: PlayerSession,
        attackDef: AttackDefinition,
        now: Long
    ) {
        val effect = attackDef.statusEffect ?: return
        val expiry = now + (effect.durationSec * 1000).toLong()
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
                            charData, derived, s.state.stance, s.combatState.attackCooldownUntilMs))
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
                charData, derived, session.state.stance, session.combatState.attackCooldownUntilMs))
    }

    fun makeStatusUpdate(
        charData: CharacterData,
        derived: DerivedStats,
        stance: PlayerStance,
        cooldownUntilMs: Long,
    ): ServerMessage.PlayerStatusUpdate {
        val isRage = charData.characterClass.classResource == ClassResource.RAGE
        return ServerMessage.PlayerStatusUpdate(
            currentHp = charData.currentHp,
            maxHp = derived.maxHp,
            currentMana = if (isRage) 0 else charData.currentMana,
            maxMana = if (isRage) 0 else derived.maxMana,
            currentRage = if (isRage) charData.currentRage else 0,
            maxRage = if (isRage) config.maxRage else 0,
            stance = stance,
            globalCooldownRemainingMs =
                (cooldownUntilMs - System.currentTimeMillis()).coerceAtLeast(0),
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
                targetId, npc.state.name, npc.state.currentHp, npc.state.maxHp, distance = dist)
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
