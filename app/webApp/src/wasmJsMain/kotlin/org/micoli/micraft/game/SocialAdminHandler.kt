package org.micoli.micraft.game

import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.jsAdminZoneWireframe
import org.micoli.micraft.babylon.jsAuctionListingsUpdate
import org.micoli.micraft.babylon.jsClaimDenied
import org.micoli.micraft.babylon.jsClaimSync
import org.micoli.micraft.babylon.jsFactionSync
import org.micoli.micraft.babylon.jsGroupSync
import org.micoli.micraft.babylon.jsGuildSync
import org.micoli.micraft.babylon.jsInstanceZonesSync
import org.micoli.micraft.babylon.jsMiniGameAction
import org.micoli.micraft.babylon.jsMiniGameRoomSync
import org.micoli.micraft.babylon.jsOpenAuctionHouse
import org.micoli.micraft.babylon.jsOpenCharacter
import org.micoli.micraft.babylon.jsScenePreviewData
import org.micoli.micraft.babylon.jsScenesSync
import org.micoli.micraft.babylon.jsSocialDenied
import org.micoli.micraft.babylon.jsSocialInvite
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.social.FactionColors

/**
 * Social (group/guild/faction), economy (auction house, claims) and admin/scene sync passthroughs.
 */
class SocialAdminHandler : ServerMessageHandler {
    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.OpenAuctionHouse -> jsOpenAuctionHouse()
            is ServerMessage.OpenCharacter -> jsOpenCharacter()
            is ServerMessage.AuctionListingsUpdate ->
                jsAuctionListingsUpdate(Json.encodeToString(msg))
            is ServerMessage.ClaimSync -> jsClaimSync(Json.encodeToString(msg))
            is ServerMessage.ClaimDenied -> jsClaimDenied(msg.reason)
            is ServerMessage.GroupSync -> jsGroupSync(Json.encodeToString(msg))
            is ServerMessage.GuildSync -> jsGuildSync(Json.encodeToString(msg))
            is ServerMessage.FactionSync -> {
                FactionColors.update(msg.definitions.associate { it.id to it.color })
                jsFactionSync(Json.encodeToString(msg))
            }
            is ServerMessage.SocialDenied -> jsSocialDenied(msg.scope, msg.reason)
            is ServerMessage.GroupInviteReceived ->
                jsSocialInvite("group", msg.groupId, "", msg.fromName)
            is ServerMessage.GuildInviteReceived ->
                jsSocialInvite("guild", msg.guildId, msg.guildName, msg.fromName)
            is ServerMessage.MiniGameRoomSync -> jsMiniGameRoomSync(Json.encodeToString(msg))
            is ServerMessage.MiniGameInviteReceived ->
                jsSocialInvite("minigame", msg.roomId, msg.gameType, msg.fromName)
            is ServerMessage.MiniGameAction -> jsMiniGameAction(Json.encodeToString(msg))
            is ServerMessage.AdminZoneWireframe -> jsAdminZoneWireframe(Json.encodeToString(msg))
            is ServerMessage.InstanceZonesSync -> jsInstanceZonesSync(Json.encodeToString(msg))
            is ServerMessage.ScenesSync -> jsScenesSync(Json.encodeToString(msg))
            is ServerMessage.ScenePreviewData -> jsScenePreviewData(Json.encodeToString(msg))
            else -> Unit
        }
}
