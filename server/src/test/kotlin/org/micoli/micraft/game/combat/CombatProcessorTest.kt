package org.micoli.micraft.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.AttackLevelDefinition
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.game.classes.ClassAttackAccess
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.classes.ClassLevelEntry
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcAttackSlot
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class CombatProcessorTest {

    private val config = CombatConfigData(maxCombatRange = 10f)

    private val level1Stats =
        AttackLevelDefinition(power = 0, weaponDice = "1d4", cooldownMs = 1000)

    // str=30 → meleeDmg=10; target AC ≤ 9 → roll(1..20)+10 always ≥ 9
    private val guaranteedHitAttack =
        AttackDefinition(damageType = DamageType.PHYSICAL, levels = mapOf(1 to level1Stats))

    private fun testChar(
        id: String,
        name: String,
        hp: Int = 20,
        str: Int = 30,
        characterClass: CharacterClass = CharacterClass.WARRIOR,
        level: Int = 1,
    ) =
        CharacterData(
            id = id,
            name = name,
            characterClass = characterClass,
            baseStats = BaseStats(str = str),
            currentHp = hp,
            currentMana = 50,
            currentRage = 50,
            level = level,
        )

    private fun buildProcessor(
        sessions: () -> Collection<PlayerSession>,
        attackRegistry: Map<String, AttackDefinition> =
            mapOf("basic_attack" to guaranteedHitAttack),
        classRegistry: Map<String, ClassDefinitionEntry> = emptyMap(),
        combatLog: MutableList<String> = mutableListOf(),
        subscribed: MutableList<Pair<PlayerSession, String>> = mutableListOf(),
    ) =
        CombatProcessor(
            config = config,
            attackRegistry = attackRegistry,
            armorRegistry = emptyMap(),
            classRegistry = classRegistry,
            npcManager = NpcManager(broadcast = {}),
            getSessions = sessions,
            broadcastCombatLog = { combatLog.add(it) },
            subscribeToChannel = { s, ch -> subscribed.add(s to ch) },
            i18n = testI18n(),
            savePlayer = {},
        )

    private fun fakeNpc(power: Int = 0): NpcInstance {
        val def =
            NpcDefinition(
                type = "zombie",
                behavior = StaticNpcBehavior(),
                bbmodelFile = "zombie.bbmodel",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 1f,
                wanderRadius = 5f,
                attacks = listOf(NpcAttackSlot("basic_attack", 1)),
                hp = 30,
                aggroMode = AggroMode.AGGRESSIVE,
            )
        val state =
            NpcState(
                id = "npc-1",
                name = "Zombie",
                type = "zombie",
                pos = Vec3(0f, 0f, 0f),
                yaw = 0f,
                currentHp = 30,
                maxHp = 30,
            )
        return NpcInstance(state = state, definition = def, spawnPos = Vec3(0f, 0f, 0f))
    }

    // ── attackPlayer ──────────────────────────────────────────────────────────

    @Test
    fun `attackPlayer targetNotFound sends notification`() = runBlocking {
        val attacker = testSession(id = "a", name = "Alice")
        attacker.characterData = testChar("a", "Alice")

        buildProcessor(sessions = { listOf(attacker) })
            .handleAttack(
                attacker,
                ClientMessage.AttackTarget(
                    attackId = "basic_attack",
                    targetId = "missing",
                    isNpc = false,
                    attackLevel = 1),
            )

        assertTrue(
            attacker.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("not found", ignoreCase = true)
            })
    }

    @Test
    fun `attackPlayer outOfRange sends notification and leaves HP unchanged`() = runBlocking {
        val attacker = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        val target = testSession(id = "b", name = "Bob", pos = Vec3(100f, 0f, 0f))
        attacker.characterData = testChar("a", "Alice")
        target.characterData = testChar("b", "Bob", hp = 20)

        buildProcessor(sessions = { listOf(attacker, target) })
            .handleAttack(
                attacker,
                ClientMessage.AttackTarget(
                    attackId = "basic_attack", targetId = "b", isNpc = false, attackLevel = 1),
            )

        assertTrue(
            attacker.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("range", ignoreCase = true)
            })
        assertEquals(20, target.characterData!!.currentHp)
    }

    @Test
    fun `attackPlayer hit reduces target HP`() = runBlocking {
        val attacker = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
        attacker.characterData = testChar("a", "Alice")
        target.characterData = testChar("b", "Bob", hp = 20)

        buildProcessor(sessions = { listOf(attacker, target) })
            .handleAttack(
                attacker,
                ClientMessage.AttackTarget(
                    attackId = "basic_attack", targetId = "b", isNpc = false, attackLevel = 1),
            )

        assertTrue(target.characterData!!.currentHp < 20)
    }

    @Test
    fun `attackPlayer hit subscribes target to combat channel`() = runBlocking {
        val attacker = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
        attacker.characterData = testChar("a", "Alice")
        target.characterData = testChar("b", "Bob")

        val subscribed = mutableListOf<Pair<PlayerSession, String>>()
        buildProcessor(sessions = { listOf(attacker, target) }, subscribed = subscribed)
            .handleAttack(
                attacker,
                ClientMessage.AttackTarget(
                    attackId = "basic_attack", targetId = "b", isNpc = false, attackLevel = 1),
            )

        assertTrue(subscribed.any { (s, ch) -> s.id == "b" && ch == "combat" })
    }

    @Test
    fun `attackPlayer broadcasts combat log`() = runBlocking {
        val attacker = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
        attacker.characterData = testChar("a", "Alice")
        target.characterData = testChar("b", "Bob")

        val combatLog = mutableListOf<String>()
        buildProcessor(sessions = { listOf(attacker, target) }, combatLog = combatLog)
            .handleAttack(
                attacker,
                ClientMessage.AttackTarget(
                    attackId = "basic_attack", targetId = "b", isNpc = false, attackLevel = 1),
            )

        assertEquals(1, combatLog.size)
        assertTrue(combatLog[0].contains("Alice"))
        assertTrue(combatLog[0].contains("Bob"))
    }

    @Test
    fun `attackPlayer hit depletes HP and downs target`() = runBlocking {
        val attacker = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
        attacker.characterData = testChar("a", "Alice")
        target.characterData = testChar("b", "Bob", hp = 1)

        buildProcessor(sessions = { listOf(attacker, target) })
            .handleAttack(
                attacker,
                ClientMessage.AttackTarget(
                    attackId = "basic_attack", targetId = "b", isNpc = false, attackLevel = 1),
            )

        assertTrue(target.isDowned)
        assertTrue(target.sent.filterIsInstance<ServerMessage.PlayerDowned>().isNotEmpty())
    }

    @Test
    fun `attackPlayer cooldown blocks second immediate attack`() = runBlocking {
        val attacker = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
        attacker.characterData = testChar("a", "Alice")
        target.characterData = testChar("b", "Bob", hp = 100)

        val combatLog = mutableListOf<String>()
        val processor =
            buildProcessor(sessions = { listOf(attacker, target) }, combatLog = combatLog)

        val msg =
            ClientMessage.AttackTarget(
                attackId = "basic_attack", targetId = "b", isNpc = false, attackLevel = 1)
        processor.handleAttack(attacker, msg)
        processor.handleAttack(attacker, msg) // blocked by cooldown

        assertEquals(1, combatLog.size)
    }

    // ── Class level gate ──────────────────────────────────────────────────────

    @Test
    fun `class gate blocks attack not in unlocked list`() = runBlocking {
        val attacker = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
        attacker.characterData = testChar("a", "Alice", level = 1)
        target.characterData = testChar("b", "Bob", hp = 20)

        val classRegistry =
            mapOf(
                "WARRIOR" to
                    ClassDefinitionEntry(
                        levels =
                            mapOf(
                                1 to
                                    ClassLevelEntry(listOf(ClassAttackAccess("basic_attack", 1))))))

        val combatLog = mutableListOf<String>()
        buildProcessor(
                sessions = { listOf(attacker, target) },
                classRegistry = classRegistry,
                combatLog = combatLog,
            )
            .handleAttack(
                attacker,
                ClientMessage.AttackTarget(
                    attackId = "basic_attack", targetId = "b", isNpc = false, attackLevel = 2),
            )

        assertEquals(0, combatLog.size)
        assertEquals(20, target.characterData!!.currentHp)
        assertTrue(
            attacker.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("cannot use", ignoreCase = true)
            })
    }

    @Test
    fun `class gate allows attack unlocked at current level`() = runBlocking {
        val attacker = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
        attacker.characterData = testChar("a", "Alice", level = 2)
        target.characterData = testChar("b", "Bob", hp = 100)

        val attack =
            AttackDefinition(
                damageType = DamageType.PHYSICAL,
                levels =
                    mapOf(
                        1 to
                            AttackLevelDefinition(power = 0, weaponDice = "1d4", cooldownMs = 1000),
                        2 to
                            AttackLevelDefinition(power = 0, weaponDice = "1d4", cooldownMs = 1000),
                    ))
        val classRegistry =
            mapOf(
                "WARRIOR" to
                    ClassDefinitionEntry(
                        levels =
                            mapOf(
                                1 to ClassLevelEntry(listOf(ClassAttackAccess("slash", 1))),
                                2 to ClassLevelEntry(listOf(ClassAttackAccess("slash", 2))))))

        val combatLog = mutableListOf<String>()
        buildProcessor(
                sessions = { listOf(attacker, target) },
                attackRegistry = mapOf("slash" to attack),
                classRegistry = classRegistry,
                combatLog = combatLog,
            )
            .handleAttack(
                attacker,
                ClientMessage.AttackTarget(
                    attackId = "slash", targetId = "b", isNpc = false, attackLevel = 2),
            )

        assertEquals(1, combatLog.size)
    }

    // ── handleNpcAttack ───────────────────────────────────────────────────────

    @Test
    fun `handleNpcAttack hit reduces target HP`() = runBlocking {
        val target = testSession(id = "b", name = "Bob", pos = Vec3(0f, 0f, 0f))
        target.characterData = testChar("b", "Bob", hp = 20)

        val highPower =
            AttackDefinition(
                damageType = DamageType.PHYSICAL,
                levels =
                    mapOf(
                        1 to
                            AttackLevelDefinition(
                                power = 100, weaponDice = "1d4", cooldownMs = 1000)))
        val proc =
            buildProcessor(
                sessions = { listOf(target) },
                attackRegistry = mapOf("basic_attack" to highPower),
            )

        proc.handleNpcAttack(fakeNpc(), target)

        assertTrue(target.characterData!!.currentHp < 20)
    }

    @Test
    fun `handleNpcAttack hit subscribes target to combat channel`() = runBlocking {
        val target = testSession(id = "b", name = "Bob", pos = Vec3(0f, 0f, 0f))
        target.characterData = testChar("b", "Bob", hp = 20)

        val subscribed = mutableListOf<Pair<PlayerSession, String>>()
        val highPower =
            AttackDefinition(
                damageType = DamageType.PHYSICAL,
                levels =
                    mapOf(
                        1 to
                            AttackLevelDefinition(
                                power = 100, weaponDice = "1d4", cooldownMs = 1000)))
        val proc =
            buildProcessor(
                sessions = { listOf(target) },
                attackRegistry = mapOf("basic_attack" to highPower),
                subscribed = subscribed,
            )

        proc.handleNpcAttack(fakeNpc(), target)

        assertTrue(subscribed.any { (s, ch) -> s.id == "b" && ch == "combat" })
    }

    @Test
    fun `handleNpcAttack on cooldown does nothing`() = runBlocking {
        val target = testSession(id = "b", name = "Bob", pos = Vec3(0f, 0f, 0f))
        target.characterData = testChar("b", "Bob", hp = 20)

        val combatLog = mutableListOf<String>()
        val proc = buildProcessor(sessions = { listOf(target) }, combatLog = combatLog)

        val npc = fakeNpc().also { it.attackCooldownsUntilMs["basic_attack:1"] = Long.MAX_VALUE }
        proc.handleNpcAttack(npc, target)

        assertEquals(0, combatLog.size)
        assertEquals(20, target.characterData!!.currentHp)
    }

    // ── HP sync to victim (regression: HP stuck at stale value) ────────────────

    @Test
    fun `attackPlayer hit sends victim a PlayerStatusUpdate with the new HP`() = runBlocking {
        val attacker = testSession(id = "a", name = "Alice", pos = Vec3(0f, 0f, 0f))
        val target = testSession(id = "b", name = "Bob", pos = Vec3(1f, 0f, 0f))
        attacker.characterData = testChar("a", "Alice")
        target.characterData = testChar("b", "Bob", hp = 20)

        buildProcessor(sessions = { listOf(attacker, target) })
            .handleAttack(
                attacker,
                ClientMessage.AttackTarget(
                    attackId = "basic_attack", targetId = "b", isNpc = false, attackLevel = 1),
            )

        val statusUpdates = target.sent.filterIsInstance<ServerMessage.PlayerStatusUpdate>()
        assertTrue(statusUpdates.isNotEmpty())
        assertEquals(target.characterData!!.currentHp, statusUpdates.last().currentHp)
    }

    // ── Downed / stabilize / death ───────────────────────────────────────────

    @Test
    fun `stabilize sends PlayerStatusUpdate with hp 1`() = runBlocking {
        val target = testSession(id = "b", name = "Bob")
        target.characterData = testChar("b", "Bob", hp = 0)
        target.combatState = target.combatState.copy(downingSuccesses = 2, downingFailures = 0)
        val proc = buildProcessor(sessions = { listOf(target) })

        var attempts = 0
        while (target.isDowned && attempts < 1000) {
            proc.tickDowningRolls(target)
            if (target.isDowned) {
                target.combatState =
                    target.combatState.copy(downingSuccesses = 2, downingFailures = 0)
            }
            attempts++
        }

        assertEquals(1, target.characterData!!.currentHp)
        val statusUpdates = target.sent.filterIsInstance<ServerMessage.PlayerStatusUpdate>()
        assertTrue(statusUpdates.any { it.currentHp == 1 })
    }

    @Test
    fun `death and respawn sends PlayerStatusUpdate matching new HP`() = runBlocking {
        val target = testSession(id = "b", name = "Bob")
        target.characterData = testChar("b", "Bob", hp = 0)
        target.combatState = target.combatState.copy(downingSuccesses = 0, downingFailures = 2)
        val proc = buildProcessor(sessions = { listOf(target) })

        var attempts = 0
        while (target.isDowned && attempts < 1000) {
            proc.tickDowningRolls(target)
            if (target.isDowned) {
                target.combatState =
                    target.combatState.copy(downingSuccesses = 0, downingFailures = 2)
            }
            attempts++
        }

        assertTrue(target.sent.filterIsInstance<ServerMessage.PlayerRespawned>().isNotEmpty())
        val statusUpdates = target.sent.filterIsInstance<ServerMessage.PlayerStatusUpdate>()
        assertTrue(statusUpdates.isNotEmpty())
        assertEquals(target.characterData!!.currentHp, statusUpdates.last().currentHp)
    }

    @Test
    fun `handleNpcAttack out of range vertically does not hit`() = runBlocking {
        val target = testSession(id = "b", name = "Bob", pos = Vec3(0f, 10f, 0f))
        target.characterData = testChar("b", "Bob", hp = 20)

        val npc = fakeNpc()
        npc.state = npc.state.copy(pos = Vec3(0f, 0f, 0f))

        val combatLog = mutableListOf<String>()
        buildProcessor(sessions = { listOf(target) }, combatLog = combatLog)
            .handleNpcAttack(npc, target)

        assertEquals(0, combatLog.size)
        assertEquals(20, target.characterData!!.currentHp)
    }

    @Test
    fun `handleNpcAttack within 3D range but elevated hits`() = runBlocking {
        val target = testSession(id = "b", name = "Bob", pos = Vec3(0f, 2f, 0f))
        target.characterData = testChar("b", "Bob", hp = 20)

        val highPower =
            AttackDefinition(
                damageType = DamageType.PHYSICAL,
                levels =
                    mapOf(
                        1 to
                            AttackLevelDefinition(
                                power = 100, weaponDice = "1d4", cooldownMs = 1000)))
        val npc = fakeNpc()
        npc.state = npc.state.copy(pos = Vec3(0f, 0f, 0f))

        buildProcessor(
                sessions = { listOf(target) },
                attackRegistry = mapOf("basic_attack" to highPower),
            )
            .handleNpcAttack(npc, target)

        assertTrue(target.characterData!!.currentHp < 20)
    }

    @Test
    fun `handleNpcAttack grants rage to WARRIOR target on hit`() = runBlocking {
        val target = testSession(id = "b", name = "Bob")
        target.characterData =
            testChar("b", "Bob", hp = 100, str = 10, characterClass = CharacterClass.WARRIOR)
        target.characterData = target.characterData!!.copy(currentRage = 0)

        val highPowerAttack =
            AttackDefinition(
                damageType = DamageType.PHYSICAL,
                levels =
                    mapOf(
                        1 to
                            AttackLevelDefinition(power = 100, weaponDice = "1d4", cooldownMs = 0)),
            )
        val npc = fakeNpc()
        npc.state = npc.state.copy(pos = Vec3(0f, 0f, 0f))
        target.state = target.state.copy(pos = Vec3(1f, 0f, 0f))

        val proc =
            buildProcessor(
                sessions = { listOf(target) },
                attackRegistry = mapOf("basic_attack" to highPowerAttack),
            )
        proc.handleNpcAttack(npc, target)

        assertEquals(
            20, target.characterData!!.currentRage, "Warrior should gain +20 rage when hit by NPC")
    }
}
