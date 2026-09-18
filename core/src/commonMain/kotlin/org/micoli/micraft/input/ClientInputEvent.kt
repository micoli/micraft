package org.micoli.micraft.input

/**
 * Parameterless client input events forwarded by `keyboard.ts` (`window.mcState.events`) and
 * consumed by `LocalPlayerController.processDiscreteEvents`. [wire] is the raw string that crosses
 * the JS/Wasm boundary — this enum is the single source of truth for it.
 * `:server:generateClientEventTypes` (`make gen-client-events`) regenerates
 * `app/webApp/ts-src/generated/input/clientEvents.ts` from these entries plus [ClientInputEvent]'s
 * payload prefixes, so the two sides can't silently drift.
 */
enum class ClientInputAction(val wire: String) {
    VIEW_TOGGLE("view_toggle"),
    INVENTORY("inventory"),
    SCREENSHOT("screenshot"),
    UNDO("undo"),
    FLY_TOGGLE("fly_toggle"),
    BLOCK_INTERACT("block_interact"),
    AUTO_FORWARD("auto_forward"),
    PLACE_ROTATE("place_rotate"),
    SLOT_1("slot_1"),
    SLOT_2("slot_2"),
    SLOT_3("slot_3"),
    SLOT_4("slot_4"),
    SLOT_5("slot_5"),
    SLOT_6("slot_6"),
    SLOT_7("slot_7"),
    SLOT_8("slot_8"),
    SLOT_9("slot_9"),
    SLOT_10("slot_10"),
    SHORTCUT_PAGE_PREV("shortcut_page_prev"),
    SHORTCUT_PAGE_NEXT("shortcut_page_next"),
    COMBAT_TARGET_CYCLE("combat_target_cycle"),
    ACTIONBLOCK_EDIT("actionblock_edit"),
    NPC_INTERACT("npc_interact"),
    VEHICLE_MOUNT("vehicle_mount"),
    TAME("tame"),
    PET_DISMISS("pet_dismiss"),
    TOGGLE_COMPASS("toggle_compass"),
    SIEGE_WEAPON_ROTATE("siege_weapon_rotate"),
    SIEGE_WEAPON_PITCH("siege_weapon_pitch"),
    SIEGE_WEAPON_POWER("siege_weapon_power"),
    SIEGE_WEAPON_FIRE("siege_weapon_fire"),
    COMBAT_ATTACK("combat_attack"),
    GROUP_CREATE("group_create"),
    GROUP_LEAVE("group_leave"),
    GROUP_DISBAND("group_disband"),
    GUILD_LEAVE("guild_leave"),
    GUILD_DISBAND("guild_disband"),
}

/**
 * Client input events carrying a payload, encoded on the wire as `<prefix><payload>` (e.g.
 * `"attack:fireball:2"`). [ClientInputEvent.parse] is the single place decoding the raw string —
 * every prefix used there is also what `:server:generateClientEventTypes` emits into the generated
 * TypeScript, so a typo in either language fails loudly (Kotlin: exhaustive `when`; TypeScript:
 * unresolved generated constant) instead of silently dropping an event at runtime.
 */
sealed class ClientInputEvent {
    data class Simple(val action: ClientInputAction) : ClientInputEvent()

    data class ShortcutPageGoto(val page: Int) : ClientInputEvent()

    data class Command(val text: String) : ClientInputEvent()

    data class Macro(val name: String) : ClientInputEvent()

    data class NpcChatSend(val json: String) : ClientInputEvent()

    data class NpcChatAcceptGift(val json: String) : ClientInputEvent()

    data class MailSend(val json: String) : ClientInputEvent()

    data class MailSeen(val mailId: String) : ClientInputEvent()

    data class MailDelete(val mailId: String) : ClientInputEvent()

    data class MailClaim(val mailId: String) : ClientInputEvent()

    data class AuctionCreate(val json: String) : ClientInputEvent()

    data class AuctionBid(val json: String) : ClientInputEvent()

    data class AuctionBuyNow(val listingId: String) : ClientInputEvent()

    data class AuctionCancel(val listingId: String) : ClientInputEvent()

    data class AuctionSetFilter(val json: String) : ClientInputEvent()

    data class ClaimCreate(val json: String) : ClientInputEvent()

    data class ClaimAbandon(val claimId: String) : ClientInputEvent()

    data class ClaimSetTrusted(val json: String) : ClientInputEvent()

    data class GroupInvite(val playerId: String) : ClientInputEvent()

    data class GroupRespond(val groupId: String, val accept: Boolean) : ClientInputEvent()

    data class GroupKick(val playerId: String) : ClientInputEvent()

    data class GroupTransfer(val playerId: String) : ClientInputEvent()

    data class GuildCreate(val name: String, val tag: String) : ClientInputEvent()

    data class GuildInvite(val playerId: String) : ClientInputEvent()

    data class GuildRespond(val guildId: String, val accept: Boolean) : ClientInputEvent()

    data class GuildKick(val playerId: String) : ClientInputEvent()

    data class GuildMotd(val text: String) : ClientInputEvent()

    data class GuildSetRank(val playerId: String, val rank: String) : ClientInputEvent()

    data class GuildRankUpsert(val json: String) : ClientInputEvent()

    data class GuildRankDelete(val rankId: String) : ClientInputEvent()

    data class GuildTransfer(val playerId: String) : ClientInputEvent()

    data class GuildBankDeposit(val item: String, val count: Int) : ClientInputEvent()

    data class GuildBankWithdraw(val item: String, val count: Int) : ClientInputEvent()

    data class FactionSet(val factionId: String?) : ClientInputEvent()

    data class Attack(val attackId: String, val rank: Int) : ClientInputEvent()

    data class Spell(val spellId: String, val rank: Int) : ClientInputEvent()

    data class CreativePlace(
        val x: Int,
        val y: Int,
        val z: Int,
        val itemId: String,
        val rotation: Int,
    ) : ClientInputEvent()

    data class CreativeFocus(val x: Float, val z: Float) : ClientInputEvent()

    data class CreativeBreak(val x: Int, val y: Int, val z: Int) : ClientInputEvent()

    data class ScenePreviewRequest(val sceneId: String) : ClientInputEvent()

    companion object {
        /** Prefix -> payload parser. Order doesn't matter: every prefix here is distinct. */
        private val PREFIX_PARSERS: List<Pair<String, (String) -> ClientInputEvent?>> =
            listOf(
                "cmd:" to { p -> Command(p) },
                "macro:" to { p -> Macro(p) },
                "npc_chat_send:" to { p -> NpcChatSend(p) },
                "npc_chat_accept_gift:" to { p -> NpcChatAcceptGift(p) },
                "mail_send:" to { p -> MailSend(p) },
                "mail_seen:" to { p -> MailSeen(p) },
                "mail_delete:" to { p -> MailDelete(p) },
                "mail_claim:" to { p -> MailClaim(p) },
                "auction_create:" to { p -> AuctionCreate(p) },
                "auction_bid:" to { p -> AuctionBid(p) },
                "auction_buynow:" to { p -> AuctionBuyNow(p) },
                "auction_cancel:" to { p -> AuctionCancel(p) },
                "auction_set_filter:" to { p -> AuctionSetFilter(p) },
                "claim_create:" to { p -> ClaimCreate(p) },
                "claim_abandon:" to { p -> ClaimAbandon(p) },
                "claim_set_trusted:" to { p -> ClaimSetTrusted(p) },
                "group_invite:" to { p -> GroupInvite(p) },
                "group_respond:" to ::parseGroupRespond,
                "group_kick:" to { p -> GroupKick(p) },
                "group_transfer:" to { p -> GroupTransfer(p) },
                "guild_create:" to ::parseGuildCreate,
                "guild_invite:" to { p -> GuildInvite(p) },
                "guild_respond:" to ::parseGuildRespond,
                "guild_kick:" to { p -> GuildKick(p) },
                "guild_motd:" to { p -> GuildMotd(p) },
                "guild_setrank:" to ::parseGuildSetRank,
                "guild_rank_upsert:" to { p -> GuildRankUpsert(p) },
                "guild_rank_delete:" to { p -> GuildRankDelete(p) },
                "guild_transfer:" to { p -> GuildTransfer(p) },
                "guild_bank_deposit:" to ::parseGuildBankDeposit,
                "guild_bank_withdraw:" to ::parseGuildBankWithdraw,
                "faction_set:" to { p -> FactionSet(p.ifBlank { null }) },
                "attack:" to ::parseAttack,
                "spell:" to ::parseSpell,
                "creative_place:" to ::parseCreativePlace,
                "creative_focus:" to ::parseCreativeFocus,
                "creative_break:" to ::parseCreativeBreak,
                "scene_preview_request:" to { p -> ScenePreviewRequest(p) },
                "shortcut_page_" to
                    { p ->
                        p.toIntOrNull()?.takeIf { it in 1..10 }?.let { ShortcutPageGoto(it) }
                    },
            )

        /** All wire prefixes recognized by [parse], in the same order as [PREFIX_PARSERS]. */
        val payloadPrefixes: List<String> = PREFIX_PARSERS.map { it.first }

        fun parse(raw: String): ClientInputEvent? {
            ClientInputAction.entries
                .firstOrNull { it.wire == raw }
                ?.let {
                    return Simple(it)
                }
            for ((prefix, parser) in PREFIX_PARSERS) {
                if (raw.startsWith(prefix)) return parser(raw.removePrefix(prefix))
            }
            return null
        }

        private fun parseGroupRespond(payload: String): ClientInputEvent? {
            val parts = payload.split("\t")
            if (parts.size != 2) return null
            return GroupRespond(parts[0], parts[1] == "1")
        }

        private fun parseGuildCreate(payload: String): ClientInputEvent? {
            val parts = payload.split("\t")
            if (parts.size != 2) return null
            return GuildCreate(parts[0], parts[1])
        }

        private fun parseGuildRespond(payload: String): ClientInputEvent? {
            val parts = payload.split("\t")
            if (parts.size != 2) return null
            return GuildRespond(parts[0], parts[1] == "1")
        }

        private fun parseGuildSetRank(payload: String): ClientInputEvent? {
            val parts = payload.split("\t")
            if (parts.size != 2) return null
            return GuildSetRank(parts[0], parts[1])
        }

        private fun parseGuildBankDeposit(payload: String): ClientInputEvent? {
            val parts = payload.split("\t")
            if (parts.size != 2) return null
            return GuildBankDeposit(parts[0], parts[1].toIntOrNull() ?: 0)
        }

        private fun parseGuildBankWithdraw(payload: String): ClientInputEvent? {
            val parts = payload.split("\t")
            if (parts.size != 2) return null
            return GuildBankWithdraw(parts[0], parts[1].toIntOrNull() ?: 0)
        }

        private fun parseAttack(payload: String): ClientInputEvent {
            val lastColon = payload.lastIndexOf(':')
            val attackId = if (lastColon > 0) payload.substring(0, lastColon) else payload
            val rank = if (lastColon > 0) payload.substring(lastColon + 1).toIntOrNull() ?: 1 else 1
            return Attack(attackId, rank)
        }

        private fun parseSpell(payload: String): ClientInputEvent {
            val lastColon = payload.lastIndexOf(':')
            val spellId = if (lastColon > 0) payload.substring(0, lastColon) else payload
            val rank = if (lastColon > 0) payload.substring(lastColon + 1).toIntOrNull() ?: 1 else 1
            return Spell(spellId, rank)
        }

        private fun parseCreativePlace(payload: String): ClientInputEvent? {
            val parts = payload.split(",")
            val x = parts.getOrNull(0)?.toIntOrNull()
            val y = parts.getOrNull(1)?.toIntOrNull()
            val z = parts.getOrNull(2)?.toIntOrNull()
            val itemId = parts.getOrNull(3)
            val rotation = parts.getOrNull(4)?.toIntOrNull() ?: 0
            if (x == null || y == null || z == null || itemId.isNullOrEmpty()) return null
            return CreativePlace(x, y, z, itemId, rotation)
        }

        private fun parseCreativeFocus(payload: String): ClientInputEvent? {
            val parts = payload.split(",")
            val x = parts.getOrNull(0)?.toFloatOrNull()
            val z = parts.getOrNull(1)?.toFloatOrNull()
            if (x == null || z == null) return null
            return CreativeFocus(x, z)
        }

        private fun parseCreativeBreak(payload: String): ClientInputEvent? {
            val parts = payload.split(",")
            val x = parts.getOrNull(0)?.toIntOrNull()
            val y = parts.getOrNull(1)?.toIntOrNull()
            val z = parts.getOrNull(2)?.toIntOrNull()
            if (x == null || y == null || z == null) return null
            return CreativeBreak(x, y, z)
        }
    }
}
