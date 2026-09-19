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
 * `"attack:fireball:2"`). [parse] is the single place decoding the raw string — every prefix used
 * there is also what `:server:generateClientEventTypes` emits into the generated TypeScript, so a
 * typo in either language fails loudly (Kotlin: exhaustive `when`; TypeScript: unresolved generated
 * constant) instead of silently dropping an event at runtime.
 *
 * This class only holds the variants with no dedicated handler (`Simple`/`ShortcutPageGoto`
 * dispatch straight to `LocalPlayerController`; `Command`/`Macro`/`FactionSet` are one-liners) plus
 * the wire-format registry ([parse]/[payloadPrefixes]), which necessarily knows every concrete
 * subclass. Every other concern's payload variants live next to their handler — see
 * `MailEvents.kt`, `AuctionEvents.kt`, `ClaimEvents.kt`, `GroupEvents.kt`, `GuildEvents.kt`,
 * `CombatIntentEvents.kt`, `CreativeEvents.kt`, `NpcChatEvents.kt`, `MiniGameEvents.kt` — each also
 * owning the payload-shape parsing its own multi-field constructors can't do inline.
 */
sealed class ClientInputEvent {
    data class Simple(val action: ClientInputAction) : ClientInputEvent()

    data class ShortcutPageGoto(val page: Int) : ClientInputEvent()

    data class Command(val text: String) : ClientInputEvent()

    data class Macro(val name: String) : ClientInputEvent()

    data class FactionSet(val factionId: String?) : ClientInputEvent()

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
                "minigame_create:" to { p -> MiniGameCreateEvent(p) },
                "minigame_invite:" to { p -> MiniGameInviteEvent(p) },
                "minigame_respond:" to { p -> MiniGameRespondEvent(p) },
                "minigame_leave:" to { p -> MiniGameLeaveEvent(p) },
                "minigame_action:" to { p -> MiniGameActionEvent(p) },
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
    }
}
