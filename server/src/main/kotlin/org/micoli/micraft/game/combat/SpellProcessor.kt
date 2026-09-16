package org.micoli.micraft.game.combat

import kotlin.math.sqrt
import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.equipment.ToolDefinition
import org.micoli.micraft.game.equipment.WeaponDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.rpg.equipmentBonuses
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.ClassResource
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SpellProcessor::class.java)

class SpellProcessor(
    @Volatile private var spellRegistry: Map<String, SpellDefinition>,
    @Volatile private var classRegistry: Map<String, ClassDefinitionEntry>,
    @Volatile private var armorRegistry: Map<String, ArmorDefinition>,
    @Volatile private var weaponRegistry: Map<String, WeaponDefinition> = emptyMap(),
    @Volatile private var toolRegistry: Map<String, ToolDefinition> = emptyMap(),
    @Volatile private var combatConfig: CombatConfigData,
    private val combatProcessor: CombatProcessor,
    private val getSessions: () -> Collection<PlayerSession> = { emptyList() },
    private val getNpcs: () -> Collection<NpcInstance> = { emptyList() },
) {
    private val cooldowns = mutableMapOf<String, Long>()

    suspend fun handleSpell(session: PlayerSession, msg: ClientMessage.UseSpell) {
        val charData =
            session.characterData
                ?: run {
                    session.send(ServerMessage.Notification("No character — use /createcharacter"))
                    return
                }
        val spell =
            spellRegistry[msg.spellId]
                ?: run {
                    log.warn("Unknown spellId '{}' from {}", msg.spellId, session.id.take(8))
                    session.send(ServerMessage.Notification("Unknown spell '${msg.spellId}'"))
                    return
                }

        val classDef = classRegistry[charData.characterClass.name]
        val unlockedSpells =
            classDef
                ?.levels
                ?.filter { (classLevel, _) -> classLevel <= charData.level }
                ?.values
                ?.flatMap { it.spells } ?: emptyList()
        if (classDef != null &&
            unlockedSpells.isNotEmpty() &&
            unlockedSpells.none { it.spell == msg.spellId && it.rank == msg.spellRank }) {
            session.send(
                ServerMessage.Notification(
                    "Your class cannot use ${msg.spellId} rank ${msg.spellRank}"))
            return
        }
        val rankDef =
            spell.ranks[msg.spellRank]
                ?: run {
                    session.send(
                        ServerMessage.Notification(
                            "Unknown rank ${msg.spellRank} for '${msg.spellId}'"))
                    return
                }

        val now = System.currentTimeMillis()
        if (now < session.combatState.attackCooldownUntilMs) {
            session.send(ServerMessage.Notification("On global cooldown"))
            return
        }
        val cdKey = "${session.id}:${msg.spellId}:${msg.spellRank}"
        val cdUntil = cooldowns[cdKey] ?: 0L
        if (now < cdUntil) {
            session.send(ServerMessage.Notification("Spell '${msg.spellId}' on cooldown"))
            return
        }

        val resource = charData.characterClass.classResource
        if (rankDef.manaCost > 0 &&
            resource == ClassResource.MANA &&
            charData.currentMana < rankDef.manaCost) {
            session.send(ServerMessage.Notification("Not enough mana"))
            return
        }
        if (rankDef.rageCost > 0 &&
            resource == ClassResource.RAGE &&
            charData.currentRage < rankDef.rageCost) {
            session.send(ServerMessage.Notification("Not enough rage"))
            return
        }

        var updated = charData
        if (rankDef.manaCost > 0 && resource == ClassResource.MANA)
            updated = updated.copy(currentMana = updated.currentMana - rankDef.manaCost)
        if (rankDef.rageCost > 0 && resource == ClassResource.RAGE)
            updated = updated.copy(currentRage = updated.currentRage - rankDef.rageCost)

        when (spell.type) {
            SpellType.TOKEN_RAGE_CONSUME -> {
                if (updated.currentTokens <= 0) {
                    session.send(ServerMessage.Notification("No rage tokens available"))
                    return
                }
                val newRage =
                    (updated.currentRage + rankDef.rageGain).coerceAtMost(combatConfig.maxRage)
                updated =
                    updated.copy(
                        currentTokens = updated.currentTokens - rankDef.tokenCost.coerceAtLeast(1),
                        currentRage = newRage,
                    )
            }
            SpellType.DIRECT_DAMAGE -> {
                if (!castDirectDamage(session, charData, rankDef)) return
            }
            SpellType.NECROTIC_AOE -> {}
        }

        session.characterData = updated
        session.combatState =
            session.combatState.copy(attackCooldownUntilMs = now + combatConfig.globalCooldownMs)
        if (rankDef.cooldownMs > 0) cooldowns[cdKey] = now + rankDef.cooldownMs

        val armors = session.state.equipmentBonuses(armorRegistry, weaponRegistry, toolRegistry)
        val derived = DerivedStatsCalculator.compute(updated, armors)
        session.send(
            combatProcessor.makeStatusUpdate(
                updated,
                derived,
                session.state.stance,
                session.combatState.attackCooldownUntilMs,
                session.combatState.attackCooldownsUntilMs,
                session.state.godMode,
            ))
    }

    /**
     * DIRECT_DAMAGE always resolves against the caster's locked combat target — never an AoE point
     * — because the shortcut bar routes every spell cast (this type included) through
     * [handleCastAoeSpell] with a computed point in front of the player, which a single-target
     * spell has no use for.
     */
    private suspend fun castDirectDamage(
        session: PlayerSession,
        charData: CharacterData,
        rankDef: SpellRankDefinition,
    ): Boolean {
        val targetId = session.combatState.targetId
        if (targetId == null) {
            session.send(ServerMessage.Notification("No target selected"))
            return false
        }
        // "p:" (not a distinct "spell" prefix) — ServerLog's client-side renderer only recognizes
        // p:/m: tokens; anything else shows up as a literal, unstyled "[x:Name]" bracket.
        val sourceLabel = "p:${charData.name}"
        if (session.combatState.targetIsNpc) {
            val npc = getNpcs().find { it.state.id == targetId && !it.isDead }
            if (npc == null) {
                session.send(ServerMessage.Notification("Target not found"))
                return false
            }
            if (session.state.pos.distanceTo(npc.state.pos) > rankDef.maxRange) {
                session.send(
                    ServerMessage.Notification(
                        "Target out of range (max ${rankDef.maxRange.toInt()} m)"))
                return false
            }
            combatProcessor.applyDirectDamageToNpc(
                session.id, npc.state.id, rankDef.power, sourceLabel)
        } else {
            val target = getSessions().find { it.id == targetId }
            if (target == null) {
                session.send(ServerMessage.Notification("Target not found"))
                return false
            }
            if (session.state.pos.distanceTo(target.state.pos) > rankDef.maxRange) {
                session.send(
                    ServerMessage.Notification(
                        "Target out of range (max ${rankDef.maxRange.toInt()} m)"))
                return false
            }
            combatProcessor.applyDirectDamage(target, rankDef.power, sourceLabel)
        }
        return true
    }

    suspend fun handleCastAoeSpell(session: PlayerSession, msg: ClientMessage.CastAoeSpell) {
        val charData =
            session.characterData
                ?: run {
                    session.send(ServerMessage.Notification("No character — use /createcharacter"))
                    return
                }
        val spell =
            spellRegistry[msg.spellId]
                ?: run {
                    log.warn("Unknown spellId '{}' from {}", msg.spellId, session.id.take(8))
                    session.send(ServerMessage.Notification("Unknown spell '${msg.spellId}'"))
                    return
                }

        val classDef = classRegistry[charData.characterClass.name]
        val unlockedSpells =
            classDef
                ?.levels
                ?.filter { (classLevel, _) -> classLevel <= charData.level }
                ?.values
                ?.flatMap { it.spells } ?: emptyList()
        if (classDef != null &&
            unlockedSpells.isNotEmpty() &&
            unlockedSpells.none { it.spell == msg.spellId && it.rank == msg.spellRank }) {
            session.send(
                ServerMessage.Notification(
                    "Your class cannot use ${msg.spellId} rank ${msg.spellRank}"))
            return
        }
        val rankDef =
            spell.ranks[msg.spellRank]
                ?: run {
                    session.send(
                        ServerMessage.Notification(
                            "Unknown rank ${msg.spellRank} for '${msg.spellId}'"))
                    return
                }

        val now = System.currentTimeMillis()
        if (now < session.combatState.attackCooldownUntilMs) {
            session.send(ServerMessage.Notification("On global cooldown"))
            return
        }
        val cdKey = "${session.id}:${msg.spellId}:${msg.spellRank}"
        val cdUntil = cooldowns[cdKey] ?: 0L
        if (now < cdUntil) {
            session.send(ServerMessage.Notification("Spell '${msg.spellId}' on cooldown"))
            return
        }

        // DIRECT_DAMAGE ignores the AoE point entirely — castDirectDamage range-checks the
        // caster's actual locked target instead.
        if (spell.type != SpellType.DIRECT_DAMAGE) {
            val pos = session.state.pos
            val dx = msg.targetX - pos.x
            val dy = msg.targetY - pos.y
            val dz = msg.targetZ - pos.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist > rankDef.maxRange) {
                session.send(
                    ServerMessage.Notification(
                        "Target out of range (max ${rankDef.maxRange.toInt()} m)"))
                return
            }
        }

        val resource = charData.characterClass.classResource
        if (rankDef.manaCost > 0 &&
            resource == ClassResource.MANA &&
            charData.currentMana < rankDef.manaCost) {
            session.send(ServerMessage.Notification("Not enough mana"))
            return
        }
        if (rankDef.rageCost > 0 &&
            resource == ClassResource.RAGE &&
            charData.currentRage < rankDef.rageCost) {
            session.send(ServerMessage.Notification("Not enough rage"))
            return
        }

        var updated = charData
        if (rankDef.manaCost > 0 && resource == ClassResource.MANA)
            updated = updated.copy(currentMana = updated.currentMana - rankDef.manaCost)
        if (rankDef.rageCost > 0 && resource == ClassResource.RAGE)
            updated = updated.copy(currentRage = updated.currentRage - rankDef.rageCost)

        when (spell.type) {
            SpellType.NECROTIC_AOE -> {
                val radiusSq = rankDef.aoeRadius * rankDef.aoeRadius
                val effect = resolveStatusEffect(rankDef.statusEffect)
                val durationSec = effect.durationSec
                val hitPlayers = mutableListOf<String>()
                val hitNpcs = mutableListOf<String>()

                for (target in getSessions()) {
                    if (target.characterData == null) continue
                    val tp = target.state.pos
                    val ex = msg.targetX - tp.x
                    val ey = msg.targetY - tp.y
                    val ez = msg.targetZ - tp.z
                    if (ex * ex + ey * ey + ez * ez <= radiusSq) {
                        combatProcessor.applyStatusEffectTo(target, effect, durationSec, now)
                        hitPlayers += target.state.name
                    }
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
                    ServerMessage.AoEEffect(
                        msg.targetX, msg.targetY, msg.targetZ, rankDef.aoeRadius)
                for (s in getSessions()) s.send(aoeMsg)
            }
            SpellType.TOKEN_RAGE_CONSUME -> {}
            SpellType.DIRECT_DAMAGE -> {
                if (!castDirectDamage(session, charData, rankDef)) return
            }
        }

        session.characterData = updated
        session.combatState =
            session.combatState.copy(attackCooldownUntilMs = now + combatConfig.globalCooldownMs)
        if (rankDef.cooldownMs > 0) cooldowns[cdKey] = now + rankDef.cooldownMs

        val armors = session.state.equipmentBonuses(armorRegistry, weaponRegistry, toolRegistry)
        val derived = DerivedStatsCalculator.compute(updated, armors)
        session.send(
            combatProcessor.makeStatusUpdate(
                updated,
                derived,
                session.state.stance,
                session.combatState.attackCooldownUntilMs,
                session.combatState.attackCooldownsUntilMs,
                session.state.godMode,
            ))
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
            spellIds.shuffled().firstNotNullOfOrNull { spellId ->
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

        for (s in getSessions()) {
            if (s.characterData == null) continue
            val sp = s.state.pos
            val ex = targetPos.x - sp.x
            val ey = targetPos.y - sp.y
            val ez = targetPos.z - sp.z
            if (ex * ex + ey * ey + ez * ez <= radiusSq) {
                combatProcessor.applyStatusEffectTo(s, effect, durationSec, now)
            }
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
        armorRegistry: Map<String, ArmorDefinition>,
        combatConfig: CombatConfigData,
        weaponRegistry: Map<String, WeaponDefinition> = this.weaponRegistry,
        toolRegistry: Map<String, ToolDefinition> = this.toolRegistry,
    ) {
        this.spellRegistry = spellRegistry
        this.classRegistry = classRegistry
        this.armorRegistry = armorRegistry
        this.combatConfig = combatConfig
        this.weaponRegistry = weaponRegistry
        this.toolRegistry = toolRegistry
    }
}
