package org.micoli.micraft.input

import org.micoli.micraft.protocol.ClientMessage

/** Land-claim payloads — grouped so `LocalPlayerController` can dispatch them in one branch. */
sealed interface ClaimEvent

data class ClaimCreate(val json: String) : ClientInputEvent(), ClaimEvent

data class ClaimAbandon(val claimId: String) : ClientInputEvent(), ClaimEvent

data class ClaimSetTrusted(val json: String) : ClientInputEvent(), ClaimEvent

class ClaimEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: ClaimEvent) {
        when (event) {
            is ClaimCreate ->
                ctx.decodeLogged<ClientMessage.ClaimCreate>(event.json, "claim_create")
            is ClaimAbandon -> ctx.outMessages.trySend(ClientMessage.ClaimAbandon(event.claimId))
            is ClaimSetTrusted ->
                ctx.decodeLogged<ClientMessage.ClaimSetTrusted>(event.json, "claim_set_trusted")
        }
    }
}
