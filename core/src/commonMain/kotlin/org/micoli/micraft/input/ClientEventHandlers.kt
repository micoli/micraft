package org.micoli.micraft.input

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ClientMessage

/**
 * What every per-concern [ClientInputEvent] handler needs — deliberately narrow (no reference to
 * the wasm/JS bridge or the full `LocalPlayerController`) so each handler runs the same on any
 * target and can be exercised without a browser/Wasm runtime.
 */
class ClientEventContext(
    val outMessages: Channel<ClientMessage>,
    val currentCombatTargetId: () -> String?,
    /** Mirrors the previous inline `jsError("$label decode failed: $it")` calls. */
    val onDecodeFailure: (label: String, error: Throwable) -> Unit = { _, _ -> },
) {
    inline fun <reified T : ClientMessage> decodeSilently(json: String) {
        runCatching { outMessages.trySend(Json.decodeFromString<T>(json)) }
    }

    inline fun <reified T : ClientMessage> decodeLogged(json: String, label: String) {
        runCatching { outMessages.trySend(Json.decodeFromString<T>(json)) }
            .onFailure { onDecodeFailure(label, it) }
    }
}

class NpcChatEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: ClientInputEvent.NpcChatEvent) {
        when (event) {
            is ClientInputEvent.NpcChatSend ->
                ctx.decodeSilently<ClientMessage.NpcChatSend>(event.json)
            is ClientInputEvent.NpcChatAcceptGift ->
                ctx.decodeSilently<ClientMessage.NpcChatAcceptGift>(event.json)
        }
    }
}

class MailEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: ClientInputEvent.MailEvent) {
        when (event) {
            is ClientInputEvent.MailSend -> ctx.decodeSilently<ClientMessage.SendMail>(event.json)
            is ClientInputEvent.MailSeen ->
                ctx.outMessages.trySend(ClientMessage.MarkMailSeen(event.mailId))
            is ClientInputEvent.MailDelete ->
                ctx.outMessages.trySend(ClientMessage.DeleteMail(event.mailId))
            is ClientInputEvent.MailClaim ->
                ctx.outMessages.trySend(ClientMessage.ClaimMailAttachments(event.mailId))
        }
    }
}

class AuctionEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: ClientInputEvent.AuctionEvent) {
        when (event) {
            is ClientInputEvent.AuctionCreate ->
                ctx.decodeLogged<ClientMessage.AuctionCreateListing>(event.json, "auction_create")
            is ClientInputEvent.AuctionBid ->
                ctx.decodeLogged<ClientMessage.AuctionPlaceBid>(event.json, "auction_bid")
            is ClientInputEvent.AuctionBuyNow ->
                ctx.outMessages.trySend(ClientMessage.AuctionBuyNow(event.listingId))
            is ClientInputEvent.AuctionCancel ->
                ctx.outMessages.trySend(ClientMessage.AuctionCancelListing(event.listingId))
            is ClientInputEvent.AuctionSetFilter ->
                ctx.decodeLogged<ClientMessage.AuctionSetFilter>(event.json, "auction_set_filter")
        }
    }
}

class ClaimEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: ClientInputEvent.ClaimEvent) {
        when (event) {
            is ClientInputEvent.ClaimCreate ->
                ctx.decodeLogged<ClientMessage.ClaimCreate>(event.json, "claim_create")
            is ClientInputEvent.ClaimAbandon ->
                ctx.outMessages.trySend(ClientMessage.ClaimAbandon(event.claimId))
            is ClientInputEvent.ClaimSetTrusted ->
                ctx.decodeLogged<ClientMessage.ClaimSetTrusted>(event.json, "claim_set_trusted")
        }
    }
}

class GroupEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: ClientInputEvent.GroupEvent) {
        when (event) {
            is ClientInputEvent.GroupInvite ->
                ctx.outMessages.trySend(ClientMessage.GroupInvite(event.playerId))
            is ClientInputEvent.GroupRespond ->
                ctx.outMessages.trySend(
                    ClientMessage.GroupInviteRespond(event.groupId, event.accept))
            is ClientInputEvent.GroupKick ->
                ctx.outMessages.trySend(ClientMessage.GroupKick(event.playerId))
            is ClientInputEvent.GroupTransfer ->
                ctx.outMessages.trySend(ClientMessage.GroupTransfer(event.playerId))
        }
    }
}

class GuildEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: ClientInputEvent.GuildEvent) {
        when (event) {
            is ClientInputEvent.GuildCreate ->
                ctx.outMessages.trySend(ClientMessage.GuildCreate(event.name, event.tag))
            is ClientInputEvent.GuildInvite ->
                ctx.outMessages.trySend(ClientMessage.GuildInvite(event.playerId))
            is ClientInputEvent.GuildRespond ->
                ctx.outMessages.trySend(
                    ClientMessage.GuildInviteRespond(event.guildId, event.accept))
            is ClientInputEvent.GuildKick ->
                ctx.outMessages.trySend(ClientMessage.GuildKick(event.playerId))
            is ClientInputEvent.GuildMotd ->
                ctx.outMessages.trySend(ClientMessage.GuildSetMotd(event.text))
            is ClientInputEvent.GuildSetRank ->
                ctx.outMessages.trySend(ClientMessage.GuildSetRank(event.playerId, event.rank))
            is ClientInputEvent.GuildRankUpsert ->
                ctx.decodeLogged<ClientMessage.GuildRankUpsert>(event.json, "guild_rank_upsert")
            is ClientInputEvent.GuildRankDelete ->
                ctx.outMessages.trySend(ClientMessage.GuildRankDelete(event.rankId))
            is ClientInputEvent.GuildTransfer ->
                ctx.outMessages.trySend(ClientMessage.GuildTransferOwner(event.playerId))
            is ClientInputEvent.GuildBankDeposit ->
                ctx.outMessages.trySend(
                    ClientMessage.GuildBankDeposit(ItemType(event.item), event.count))
            is ClientInputEvent.GuildBankWithdraw ->
                ctx.outMessages.trySend(
                    ClientMessage.GuildBankWithdraw(ItemType(event.item), event.count))
        }
    }
}

class CombatIntentEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: ClientInputEvent.CombatIntentEvent) {
        when (event) {
            is ClientInputEvent.Attack -> {
                val targetId = ctx.currentCombatTargetId() ?: return
                ctx.outMessages.trySend(
                    ClientMessage.AttackTarget(
                        targetId = targetId,
                        isNpc = true,
                        attackId = event.attackId,
                        attackRank = event.rank))
            }
            is ClientInputEvent.Spell ->
                ctx.outMessages.trySend(
                    ClientMessage.UseSpell(spellId = event.spellId, spellRank = event.rank))
        }
    }
}

class CreativeEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: ClientInputEvent.CreativeEvent) {
        when (event) {
            is ClientInputEvent.CreativePlace ->
                ctx.outMessages.trySend(
                    ClientMessage.BlockPlace(
                        BlockPos(event.x, event.y, event.z),
                        ItemType(event.itemId),
                        event.rotation.toByte()))
            is ClientInputEvent.CreativeFocus ->
                ctx.outMessages.trySend(ClientMessage.CreativeCameraFocus(event.x, event.z))
            is ClientInputEvent.CreativeBreak ->
                ctx.outMessages.trySend(
                    ClientMessage.BlockBreakStart(BlockPos(event.x, event.y, event.z)))
            is ClientInputEvent.ScenePreviewRequest ->
                ctx.outMessages.trySend(ClientMessage.RequestScenePreview(event.sceneId))
        }
    }
}
