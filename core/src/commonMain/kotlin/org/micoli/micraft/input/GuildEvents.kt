package org.micoli.micraft.input

import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ClientMessage

/** Guild payloads — grouped so `LocalPlayerController` can dispatch them in one branch. */
sealed interface GuildEvent

data class GuildCreate(val name: String, val tag: String) : ClientInputEvent(), GuildEvent

data class GuildInvite(val playerId: String) : ClientInputEvent(), GuildEvent

data class GuildRespond(val guildId: String, val accept: Boolean) : ClientInputEvent(), GuildEvent

data class GuildKick(val playerId: String) : ClientInputEvent(), GuildEvent

data class GuildMotd(val text: String) : ClientInputEvent(), GuildEvent

data class GuildSetRank(val playerId: String, val rank: String) : ClientInputEvent(), GuildEvent

data class GuildRankUpsert(val json: String) : ClientInputEvent(), GuildEvent

data class GuildRankDelete(val rankId: String) : ClientInputEvent(), GuildEvent

data class GuildTransfer(val playerId: String) : ClientInputEvent(), GuildEvent

data class GuildBankDeposit(val item: String, val count: Int) : ClientInputEvent(), GuildEvent

data class GuildBankWithdraw(val item: String, val count: Int) : ClientInputEvent(), GuildEvent

/** `"<name>\t<tag>"` — the two-field wire shape this event's constructor can't parse itself. */
internal fun parseGuildCreate(payload: String): ClientInputEvent? {
    val parts = payload.split("\t")
    if (parts.size != 2) return null
    return GuildCreate(parts[0], parts[1])
}

/** `"<guildId>\t<0|1>"` */
internal fun parseGuildRespond(payload: String): ClientInputEvent? {
    val parts = payload.split("\t")
    if (parts.size != 2) return null
    return GuildRespond(parts[0], parts[1] == "1")
}

/** `"<playerId>\t<rank>"` */
internal fun parseGuildSetRank(payload: String): ClientInputEvent? {
    val parts = payload.split("\t")
    if (parts.size != 2) return null
    return GuildSetRank(parts[0], parts[1])
}

/** `"<item>\t<count>"` */
internal fun parseGuildBankDeposit(payload: String): ClientInputEvent? {
    val parts = payload.split("\t")
    if (parts.size != 2) return null
    return GuildBankDeposit(parts[0], parts[1].toIntOrNull() ?: 0)
}

/** `"<item>\t<count>"` */
internal fun parseGuildBankWithdraw(payload: String): ClientInputEvent? {
    val parts = payload.split("\t")
    if (parts.size != 2) return null
    return GuildBankWithdraw(parts[0], parts[1].toIntOrNull() ?: 0)
}

class GuildEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: GuildEvent) {
        when (event) {
            is GuildCreate ->
                ctx.outMessages.trySend(ClientMessage.GuildCreate(event.name, event.tag))
            is GuildInvite -> ctx.outMessages.trySend(ClientMessage.GuildInvite(event.playerId))
            is GuildRespond ->
                ctx.outMessages.trySend(
                    ClientMessage.GuildInviteRespond(event.guildId, event.accept))
            is GuildKick -> ctx.outMessages.trySend(ClientMessage.GuildKick(event.playerId))
            is GuildMotd -> ctx.outMessages.trySend(ClientMessage.GuildSetMotd(event.text))
            is GuildSetRank ->
                ctx.outMessages.trySend(ClientMessage.GuildSetRank(event.playerId, event.rank))
            is GuildRankUpsert ->
                ctx.decodeLogged<ClientMessage.GuildRankUpsert>(event.json, "guild_rank_upsert")
            is GuildRankDelete ->
                ctx.outMessages.trySend(ClientMessage.GuildRankDelete(event.rankId))
            is GuildTransfer ->
                ctx.outMessages.trySend(ClientMessage.GuildTransferOwner(event.playerId))
            is GuildBankDeposit ->
                ctx.outMessages.trySend(
                    ClientMessage.GuildBankDeposit(ItemType(event.item), event.count))
            is GuildBankWithdraw ->
                ctx.outMessages.trySend(
                    ClientMessage.GuildBankWithdraw(ItemType(event.item), event.count))
        }
    }
}
