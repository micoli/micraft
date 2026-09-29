package org.micoli.micraft.game.combat

import kotlin.math.sqrt
import kotlin.random.Random
import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SpellProcessor::class.java)

class SpellProcessor(
    @Volatile private var spellRegistry: Map<String, SpellDefinition>,
    @Volatile private var classRegistry: Map<String, ClassDefinitionEntry>,
    @Volatile private var combatConfig: CombatConfigData,
    private val combatProcessor: CombatProcessor,
    private val getSessions: () -> Collection<PlayerSession> = { emptyList() },
    private val getNpcs: () -> Collection<NpcInstance> = { emptyList() },
    /** Every die this processor rolls comes from here — supplied per World (ADR-0012). */
    private val rollSource: Random = Random.Default,
) {
    private val gate = AbilityGate(classRegistry, combatConfig.globalCooldownMs)

    suspend fun handleSpell(session: PlayerSession, msg: ClientMessage.UseSpell) {
        val use = clear(session, msg.spellId, msg.spellRank) ?: return
        val spell = spellRegistry.getValue(msg.spellId)
        val rankDef = spell.ranks.getValue(msg.spellRank)

        when (spell.type) {
            SpellType.NECROTIC_AOE -> {}
            SpellType.TOKEN_RAGE_CONSUME -> {}
            SpellType.DIRECT_DAMAGE -> if (!castDirectDamage(session, rankDef)) return
            SpellType.PROTECTION -> applyProtection(session, msg.spellId, msg.spellRank, rankDef)
        }
        gate.commit(session, use)
        if (spell.type == SpellType.TOKEN_RAGE_CONSUME) grantRage(session, rankDef)

        combatProcessor.characterStats.sendStatus(session)
    }

    /**
     * `/protect` (no argument): resolves the caller's Class's Protection Spell and the Rank their
     * current Level unlocks, then casts it through the same [AbilityGate] path as every other Spell
     * (Global cooldown, Cooldown, resource cost).
     */
    suspend fun castOwnProtection(session: PlayerSession) {
        val character = session.characterData
        if (character == null) {
            notify(session, "combat:server:no_character")
            return
        }
        val (spellId, spell) = ownProtectionSpell(character.characterClass.name, character.level)
        val rank = spellId?.let { spell?.usableRank(character.level) }
        if (spellId == null || spell == null || rank == null) {
            notify(session, "combat:server:no_protection")
            return
        }
        val use = clear(session, spellId, rank) ?: return
        val rankDef = spell.ranks.getValue(rank)
        applyProtection(session, spellId, rank, rankDef)
        gate.commit(session, use)
        combatProcessor.characterStats.sendStatus(session)
    }

    /** The Class's granted Spell of type [SpellType.PROTECTION], if any, and its definition. */
    private fun ownProtectionSpell(
        className: String,
        level: Int,
    ): Pair<String?, SpellDefinition?> {
        val classDef = classRegistry[className] ?: return null to null
        val spellId =
            classDef.levels
                .filterKeys { it <= level }
                .values
                .flatMap { it.spells }
                .map { it.spell }
                .distinct()
                .firstOrNull { spellRegistry[it]?.type == SpellType.PROTECTION }
        return spellId to spellId?.let { spellRegistry[it] }
    }

    private suspend fun applyProtection(
        session: PlayerSession,
        spellId: String,
        rank: Int,
        rankDef: SpellRankDefinition,
    ) {
        // Falls back to the marker's own duration (like CombatProcessor.applyStatusEffect does for
        // attack-triggered effects) so a misconfigured Rank (durationSec unset/0) never grants a
        // Protection that's already expired the moment it's cast.
        val durationSec =
            rankDef.durationSec.takeIf { it > 0f } ?: StatusEffect.Protected.durationSec
        combatProcessor.applyStatusEffectTo(
            session, StatusEffect.Protected, durationSec, System.currentTimeMillis(), spellId, rank)
    }

    private fun grantRage(session: PlayerSession, rankDef: SpellRankDefinition) {
        val character = session.characterData ?: return
        session.characterData =
            character.copy(
                currentRage =
                    (character.currentRage + rankDef.rageGain).coerceAtMost(combatConfig.maxRage))
    }

    /** Runs the gate; a refusal is sent to the caster and yields null. */
    private suspend fun clear(session: PlayerSession, spellId: String, rank: Int): AbilityUse? {
        val spell = spellRegistry[spellId]
        if (spell == null) log.warn("Unknown spellId '{}' from {}", spellId, session.id.take(8))
        val verdict =
            gate.check(
                session,
                AbilityKind.SPELL,
                spellId,
                rank,
                spell?.ranks?.mapValues { (_, rankDef) ->
                    AbilityRank(rankDef.abilityCost(spell.type), rankDef.cooldownMs)
                })
        return when (verdict) {
            is AbilityVerdict.Cleared -> verdict.ability
            is AbilityVerdict.Refused -> {
                session.send(
                    verdict.notification(
                        combatProcessor.i18n, session, AbilityKind.SPELL, spellId, rank))
                null
            }
        }
    }

    private suspend fun notify(session: PlayerSession, key: String, vararg args: Any) =
        session.send(
            ServerMessage.Notification(combatProcessor.i18n.t(session.state.language, key, *args)))

    /**
     * DIRECT_DAMAGE always resolves against the caster's locked combat target — never an AoE point
     * — because the shortcut bar routes every spell cast (this type included) through
     * [handleCastAoeSpell] with a computed point in front of the player, which a single-target
     * spell has no use for.
     */
    private suspend fun castDirectDamage(
        session: PlayerSession,
        rankDef: SpellRankDefinition,
    ): Boolean {
        val charData = session.characterData ?: return false
        val targetId = session.combatState.targetId
        if (targetId == null) {
            notify(session, "combat:server:no_target")
            return false
        }
        // "p:" (not a distinct "spell" prefix) — ServerLog's client-side renderer only recognizes
        // p:/m: tokens; anything else shows up as a literal, unstyled "[x:Name]" bracket.
        val sourceLabel = "p:${charData.name}"
        if (session.combatState.targetIsNpc) {
            val npc = getNpcs().find { it.state.id == targetId && !it.isDead }
            if (npc == null) {
                notify(session, "combat:server:target_not_found")
                return false
            }
            if (session.state.pos.distanceTo(npc.state.pos) > rankDef.maxRange) {
                notify(session, "combat:server:out_of_range_max", rankDef.maxRange.toInt())
                return false
            }
            combatProcessor.applyDirectDamageToNpc(
                session.id, npc.state.id, rankDef.power, sourceLabel)
        } else {
            val target = getSessions().find { it.id == targetId }
            if (target == null) {
                notify(session, "combat:server:target_not_found")
                return false
            }
            if (session.state.pos.distanceTo(target.state.pos) > rankDef.maxRange) {
                notify(session, "combat:server:out_of_range_max", rankDef.maxRange.toInt())
                return false
            }
            combatProcessor.applyDirectDamage(target, rankDef.power, sourceLabel)
        }
        return true
    }

    suspend fun handleCastAoeSpell(session: PlayerSession, msg: ClientMessage.CastAoeSpell) {
        val use = clear(session, msg.spellId, msg.spellRank) ?: return
        val spell = spellRegistry.getValue(msg.spellId)
        val rankDef = spell.ranks.getValue(msg.spellRank)
        val now = System.currentTimeMillis()

        // DIRECT_DAMAGE and PROTECTION ignore the AoE point entirely — DIRECT_DAMAGE
        // range-checks the caster's locked target instead, PROTECTION is self-targeted.
        if (spell.type != SpellType.DIRECT_DAMAGE && spell.type != SpellType.PROTECTION) {
            val pos = session.state.pos
            val dx = msg.targetX - pos.x
            val dy = msg.targetY - pos.y
            val dz = msg.targetZ - pos.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist > rankDef.maxRange) {
                notify(session, "combat:server:out_of_range_max", rankDef.maxRange.toInt())
                return
            }
        }

        when (spell.type) {
            SpellType.NECROTIC_AOE -> castNecroticAoe(msg, rankDef, now)
            SpellType.TOKEN_RAGE_CONSUME -> {}
            SpellType.DIRECT_DAMAGE -> if (!castDirectDamage(session, rankDef)) return
            SpellType.PROTECTION -> applyProtection(session, msg.spellId, msg.spellRank, rankDef)
        }

        gate.commit(session, use)
        combatProcessor.characterStats.sendStatus(session)
    }

    private suspend fun castNecroticAoe(
        msg: ClientMessage.CastAoeSpell,
        rankDef: SpellRankDefinition,
        now: Long,
    ) {
        val radiusSq = rankDef.aoeRadius * rankDef.aoeRadius
        val effect = resolveStatusEffect(rankDef.statusEffect)
        val durationSec = effect.durationSec
        val hitPlayers = mutableListOf<String>()
        val hitNpcs = mutableListOf<String>()

        val charSessions = getSessions().mapNotNull { s -> s.characterData?.let { s to it } }
        for ((target, targetChar) in charSessions) {
            val tp = target.state.pos
            val ex = msg.targetX - tp.x
            val ey = msg.targetY - tp.y
            val ez = msg.targetZ - tp.z
            val inRadius = ex * ex + ey * ey + ez * ez <= radiusSq
            val avoided =
                inRadius &&
                    rollAvoided(
                        rollSource,
                        DamageType.NECROTIC,
                        combatProcessor.characterStats.derived(target, targetChar))
            if (!inRadius || avoided) continue
            combatProcessor.applyStatusEffectTo(target, effect, durationSec, now)
            hitPlayers += target.state.name
        }

        for (npc in getNpcs()) {
            if (npc.isDead) continue
            val np = npc.state.pos
            val ex = msg.targetX - np.x
            val ey = msg.targetY - np.y
            val ez = msg.targetZ - np.z
            if (ex * ex + ey * ey + ez * ez <= radiusSq) {
                npc.activeEffects.removeAll { it.effect::class == effect::class }
                npc.activeEffects.add(
                    ActiveStatusEffect(effect, now + (durationSec * 1000).toLong()))
                hitNpcs += "${npc.state.id.take(8)}(${npc.state.name})"
            }
        }

        log.debug("AoE hit players={} npcs={}", hitPlayers, hitNpcs)
        val aoeMsg =
            ServerMessage.AoEEffect(msg.targetX, msg.targetY, msg.targetZ, rankDef.aoeRadius)
        for (s in getSessions()) s.send(aoeMsg)
    }

    /**
     * NPC-initiated counterpart to [handleCastAoeSpell]: a boss-tier NPC (`spells:` in its yaml)
     * fires a NECROTIC_AOE spell centered on its current target, on its own cooldown. Mirrors the
     * player path's hit-resolution but never touches mana/rage (NPCs don't have a spellcaster
     * resource loop) and excludes the caster itself from the blast.
     */
    suspend fun tryNpcCast(npc: NpcInstance, target: PlayerSession): Boolean {
        val spellIds = npc.definition.spells
        if (spellIds.isEmpty()) return false
        val now = System.currentTimeMillis()

        val targetPos = target.state.pos
        val npcPos = npc.state.pos
        val dx = targetPos.x - npcPos.x
        val dy = targetPos.y - npcPos.y
        val dz = targetPos.z - npcPos.z
        val distSq = dx * dx + dy * dy + dz * dz

        val cast =
            spellIds.shuffled(rollSource).firstNotNullOfOrNull { spellId ->
                val spell = spellRegistry[spellId] ?: return@firstNotNullOfOrNull null
                if (spell.type != SpellType.NECROTIC_AOE || !spell.enabled)
                    return@firstNotNullOfOrNull null
                val rankDef =
                    spell.ranks.entries.maxByOrNull { it.key }?.value
                        ?: return@firstNotNullOfOrNull null
                if (now < (npc.spellCooldownsUntilMs[spellId] ?: 0L))
                    return@firstNotNullOfOrNull null
                if (distSq > rankDef.maxRange * rankDef.maxRange) return@firstNotNullOfOrNull null
                Triple(spellId, spell, rankDef)
            } ?: return false

        val (spellId, _, rankDef) = cast
        npc.spellCooldownsUntilMs[spellId] = now + rankDef.cooldownMs

        val radiusSq = rankDef.aoeRadius * rankDef.aoeRadius
        val effect = resolveStatusEffect(rankDef.statusEffect)
        val durationSec = effect.durationSec

        val charSessions = getSessions().mapNotNull { s -> s.characterData?.let { s to it } }
        for ((s, sChar) in charSessions) {
            val sp = s.state.pos
            val ex = targetPos.x - sp.x
            val ey = targetPos.y - sp.y
            val ez = targetPos.z - sp.z
            val inRadius = ex * ex + ey * ey + ez * ez <= radiusSq
            val avoided =
                inRadius &&
                    rollAvoided(
                        rollSource,
                        DamageType.NECROTIC,
                        combatProcessor.characterStats.derived(s, sChar))
            if (!inRadius || avoided) continue
            combatProcessor.applyStatusEffectTo(s, effect, durationSec, now)
        }
        for (other in getNpcs()) {
            if (other.isDead || other.state.id == npc.state.id) continue
            val op = other.state.pos
            val ex = targetPos.x - op.x
            val ey = targetPos.y - op.y
            val ez = targetPos.z - op.z
            if (ex * ex + ey * ey + ez * ez <= radiusSq) {
                other.activeEffects.removeAll { it.effect::class == effect::class }
                other.activeEffects.add(
                    ActiveStatusEffect(effect, now + (durationSec * 1000).toLong()))
            }
        }

        val aoeMsg =
            ServerMessage.AoEEffect(targetPos.x, targetPos.y, targetPos.z, rankDef.aoeRadius)
        for (s in getSessions()) s.send(aoeMsg)
        log.debug("NPC {} cast {} at {}", npc.state.name, spellId, targetPos)
        return true
    }

    fun reload(
        spellRegistry: Map<String, SpellDefinition>,
        classRegistry: Map<String, ClassDefinitionEntry>,
        combatConfig: CombatConfigData,
    ) {
        this.spellRegistry = spellRegistry
        this.classRegistry = classRegistry
        this.combatConfig = combatConfig
        gate.reload(classRegistry, combatConfig.globalCooldownMs)
    }
}

private fun SpellRankDefinition.abilityCost(type: SpellType) =
    AbilityCost(
        mana = manaCost,
        rage = rageCost,
        tokens = if (type == SpellType.TOKEN_RAGE_CONSUME) tokenCost.coerceAtLeast(1) else 0)
