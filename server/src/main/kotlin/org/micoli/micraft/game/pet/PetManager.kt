package org.micoli.micraft.game.pet

import java.util.UUID
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcBehaviorRegistry
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.rpg.ExperienceProcessor
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.pet.PetRecord
import org.micoli.micraft.protocol.PetInfo
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("PetManager")

/**
 * Owns the tamed-pet roster lifecycle: summon / dismiss / death / resurrect / rename, and pushes
 * [ServerMessage.PetRosterSync] to the owner. A pet is a normal [NpcInstance] tagged with
 * [NpcInstance.ownerId]; its combat targeting is driven by [PetCoordinator].
 */
class PetManager(
    private val npcManager: NpcManager,
    private val experienceProcessor: ExperienceProcessor,
    private val getSessions: () -> Collection<PlayerSession>,
    private val savePlayer: suspend (PlayerSession) -> Unit,
    private val i18n: I18nConfig,
) {
    private fun t(session: PlayerSession, key: String, vararg args: Any) =
        i18n.t(session.state.language, key, *args)

    private suspend fun notify(session: PlayerSession, key: String, vararg args: Any) {
        session.send(ServerMessage.Notification(t(session, key, *args)))
    }

    private fun records(session: PlayerSession) = session.state.pets

    private fun findRecord(session: PlayerSession, name: String): PetRecord? =
        records(session).firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun activeInstance(session: PlayerSession): NpcInstance? =
        session.state.activePetId?.let { recordId ->
            npcManager.ownedPets().firstOrNull {
                it.ownerId == session.id && it.petRecordId == recordId
            }
        }

    private fun petDef(def: NpcDefinition): NpcDefinition =
        def.copy(
            behavior = NpcBehaviorRegistry.get("random_movable"),
            behaviorKey = "random_movable",
            aggroMode = AggroMode.PASSIVE,
            spawn = def.spawn.copy(autoSpawn = false),
            animalConfig = null,
            packConfig = null,
            hibernation = null,
        )

    private fun updateRecord(
        session: PlayerSession,
        id: String,
        transform: (PetRecord) -> PetRecord
    ) {
        session.state =
            session.state.copy(
                pets = session.state.pets.map { if (it.id == id) transform(it) else it })
    }

    suspend fun rosterSyncFor(session: PlayerSession) {
        val active = activeInstance(session)
        val infos =
            records(session).map { r ->
                val live = if (r.id == session.state.activePetId) active else null
                val maxHp = live?.maxHp ?: npcManager.definitionMaxHp(r.npcType, r.level)
                PetInfo(
                    id = r.id,
                    name = r.name,
                    npcType = r.npcType,
                    level = live?.instanceLevel ?: r.level,
                    xp = live?.xp ?: r.xp,
                    currentHp = live?.currentHp ?: r.currentHp.takeIf { it in 1..maxHp } ?: maxHp,
                    maxHp = maxHp,
                    spawned = live != null,
                    dead = r.dead,
                    resurrectReadyAtMs = r.resurrectReadyAtMs,
                )
            }
        session.send(ServerMessage.PetRosterSync(infos, session.state.activePetId))
    }

    /** Add a freshly tamed pet to the roster. Returns the created record. */
    suspend fun addTamed(
        session: PlayerSession,
        npcType: String,
        name: String,
        level: Int,
        xp: Int,
    ): PetRecord {
        val record =
            PetRecord(
                id = UUID.randomUUID().toString(),
                npcType = npcType,
                name = name,
                level = level,
                xp = xp,
                currentHp = 0,
                tamedAtLevel = level,
            )
        session.state = session.state.copy(pets = session.state.pets + record)
        savePlayer(session)
        rosterSyncFor(session)
        return record
    }

    suspend fun summon(session: PlayerSession, petName: String) {
        val record =
            findRecord(session, petName) ?: return notify(session, "pet:server:not_found", petName)
        if (record.dead) {
            val now = System.currentTimeMillis()
            if (now < record.resurrectReadyAtMs) {
                val secs = ((record.resurrectReadyAtMs - now) / 1000) + 1
                return notify(session, "pet:server:resurrect_on_cooldown", secs)
            }
            return notify(session, "pet:server:is_dead", record.name)
        }

        // One active pet: quietly retire whatever is out.
        if (session.state.activePetId != null) dismiss(session, notify = false)

        val pos = session.state.pos
        val spawnPos = Vec3(pos.x + 0.6f, pos.y, pos.z + 0.6f)
        val instance =
            npcManager.spawnNpc(
                name = record.name,
                type = record.npcType,
                pos = spawnPos,
                instanceLevel = record.level,
                defOverride = ::petDef,
            )
        instance.ownerId = session.id
        instance.petRecordId = record.id
        instance.xp = record.xp
        val maxHp = instance.maxHp
        val hp = record.currentHp.takeIf { it in 1..maxHp } ?: maxHp
        instance.currentHp = hp
        instance.state =
            instance.state.copy(
                ownerId = session.id,
                xp = record.xp,
                currentHp = hp,
                maxHp = maxHp,
            )
        npcManager.refreshNpcState(instance.state.id)

        session.state = session.state.copy(activePetId = record.id)
        savePlayer(session)
        rosterSyncFor(session)
        notify(session, "pet:server:spawned", record.name)
        log.info("Player {} summoned pet {} ({})", session.state.name, record.name, record.npcType)
    }

    suspend fun dismiss(session: PlayerSession, notify: Boolean = true) {
        val instance = activeInstance(session)
        if (instance == null) {
            session.state = session.state.copy(activePetId = null)
            if (notify) notify(session, "pet:server:none_active")
            return
        }
        val recordId = instance.petRecordId
        if (recordId != null) {
            updateRecord(session, recordId) {
                it.copy(
                    level = instance.instanceLevel,
                    xp = instance.xp,
                    currentHp = instance.currentHp,
                )
            }
        }
        npcManager.despawnNpc(instance.state.id)
        session.state = session.state.copy(activePetId = null)
        savePlayer(session)
        rosterSyncFor(session)
        if (notify) notify(session, "pet:server:dismissed", instance.state.name)
    }

    /** A summoned pet died. Wired to [NpcManager.onPetDied]. */
    suspend fun onPetDied(petInstance: NpcInstance) {
        val ownerId = petInstance.ownerId ?: return
        val recordId = petInstance.petRecordId ?: return
        val session = getSessions().firstOrNull { it.id == ownerId } ?: return
        updateRecord(session, recordId) {
            it.copy(
                level = petInstance.instanceLevel,
                xp = petInstance.xp,
                currentHp = 0,
                dead = true,
                resurrectReadyAtMs = System.currentTimeMillis() + PET_RESURRECT_COOLDOWN_MS,
            )
        }
        if (session.state.activePetId == recordId) {
            session.state = session.state.copy(activePetId = null)
        }
        savePlayer(session)
        rosterSyncFor(session)
        notify(session, "pet:server:died", petInstance.state.name)
    }

    suspend fun resurrect(session: PlayerSession, petName: String) {
        val record =
            findRecord(session, petName) ?: return notify(session, "pet:server:not_found", petName)
        if (!record.dead) return notify(session, "pet:server:resurrect_not_dead", record.name)
        val now = System.currentTimeMillis()
        if (now < record.resurrectReadyAtMs) {
            val secs = ((record.resurrectReadyAtMs - now) / 1000) + 1
            return notify(session, "pet:server:resurrect_on_cooldown", secs)
        }
        updateRecord(session, record.id) {
            it.copy(dead = false, currentHp = 0, resurrectReadyAtMs = 0L)
        }
        savePlayer(session)
        rosterSyncFor(session)
        notify(session, "pet:server:resurrected", record.name)
    }

    suspend fun rename(session: PlayerSession, oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.length > 24) {
            return notify(session, "pet:server:rename_invalid")
        }
        val record =
            findRecord(session, oldName) ?: return notify(session, "pet:server:not_found", oldName)
        if (records(session).any {
            it.id != record.id && it.name.equals(trimmed, ignoreCase = true)
        }) {
            return notify(session, "pet:server:rename_taken", trimmed)
        }
        updateRecord(session, record.id) { it.copy(name = trimmed) }
        activeInstance(session)?.let { instance ->
            instance.state = instance.state.copy(name = trimmed)
            npcManager.refreshNpcState(instance.state.id)
        }
        savePlayer(session)
        rosterSyncFor(session)
        notify(session, "pet:server:renamed", oldName, trimmed)
    }

    /** Wild NPC [killed] died — grant its XP to any credited owner's active pet. */
    suspend fun grantSharedXpForKill(killed: NpcInstance) {
        val contributors = killed.damageContributors.keys.toSet()
        if (contributors.isEmpty()) return
        val shareXp = (killed.definition.xpReward.takeIf { it > 0 } ?: (killed.instanceLevel * 4))
        for (session in getSessions()) {
            if (session.id !in contributors) continue
            val pet = activeInstance(session) ?: continue
            experienceProcessor.grantXpToNpc(pet, shareXp)
            updateRecord(session, pet.petRecordId ?: continue) {
                it.copy(level = pet.instanceLevel, xp = pet.xp)
            }
            savePlayer(session)
            rosterSyncFor(session)
        }
    }

    /** A pet leveled up (wired to [ExperienceProcessor.onNpcLevelUp]). */
    suspend fun onPetLevelUp(petInstance: NpcInstance, newLevel: Int) {
        val ownerId = petInstance.ownerId ?: return
        val recordId = petInstance.petRecordId ?: return
        val session = getSessions().firstOrNull { it.id == ownerId } ?: return
        updateRecord(session, recordId) { it.copy(level = newLevel, xp = petInstance.xp) }
        savePlayer(session)
        rosterSyncFor(session)
        notify(session, "pet:server:leveled", petInstance.state.name, newLevel)
    }

    suspend fun onPlayerDisconnected(session: PlayerSession) {
        if (session.state.activePetId != null) dismiss(session, notify = false)
    }

    companion object {
        const val PET_RESURRECT_COOLDOWN_MS = 60_000L
        const val PET_ROSTER_CAP = 12
    }
}
