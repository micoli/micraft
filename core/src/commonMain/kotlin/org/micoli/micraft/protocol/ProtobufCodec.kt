@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.micoli.micraft.protocol

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

object ServerMessageCodec {
    private val proto = ProtoBuf

    private enum class Id(val b: Byte) {
        WELCOME(0),
        SHADERS_UPDATE(1),
        CHUNK_DATA(2),
        PLAYER_UPDATE(3),
        WORLD_UPDATE(4),
        PLAYER_LEFT(5),
        BLOCK_BREAK_PROGRESS(6),
        NOTIFICATION(7),
        CHAT_MESSAGE(8),
        CHANNELS_SYNC(9),
        ITEMS_SPAWNED(10),
        ITEM_DESPAWNED(11),
        INVENTORY_UPDATE(12),
        TIME_UPDATE(13),
        SHORTCUT_BAR_UPDATE(14),
        LAYOUTS_SYNC(15),
        OPEN_LAYOUT_EDITOR(16),
        OPEN_PREFERENCES(17),
        OPEN_CODEX(18),
        REGISTRY_SYNC(19),
        NPC_SPAWNED(20),
        NPC_DESPAWNED(21),
        NPC_UPDATE(22),
        NPC_INTERACT_RESULT(23),
        PREFERENCES_SYNC(24),
        WEATHER_UPDATE(25),
    }

    fun encode(msg: ServerMessage): ByteArray {
        val (id, payload) =
            when (msg) {
                is ServerMessage.Welcome -> Id.WELCOME to proto.encodeToByteArray(msg)
                is ServerMessage.ShadersUpdate -> Id.SHADERS_UPDATE to proto.encodeToByteArray(msg)
                is ServerMessage.ChunkData -> Id.CHUNK_DATA to proto.encodeToByteArray(msg)
                is ServerMessage.PlayerUpdate -> Id.PLAYER_UPDATE to proto.encodeToByteArray(msg)
                is ServerMessage.WorldUpdate -> Id.WORLD_UPDATE to proto.encodeToByteArray(msg)
                is ServerMessage.PlayerLeft -> Id.PLAYER_LEFT to proto.encodeToByteArray(msg)
                is ServerMessage.BlockBreakProgress ->
                    Id.BLOCK_BREAK_PROGRESS to proto.encodeToByteArray(msg)
                is ServerMessage.Notification -> Id.NOTIFICATION to proto.encodeToByteArray(msg)
                is ServerMessage.ChatMessage -> Id.CHAT_MESSAGE to proto.encodeToByteArray(msg)
                is ServerMessage.ChannelsSync -> Id.CHANNELS_SYNC to proto.encodeToByteArray(msg)
                is ServerMessage.ItemsSpawned -> Id.ITEMS_SPAWNED to proto.encodeToByteArray(msg)
                is ServerMessage.ItemDespawned -> Id.ITEM_DESPAWNED to proto.encodeToByteArray(msg)
                is ServerMessage.InventoryUpdate ->
                    Id.INVENTORY_UPDATE to proto.encodeToByteArray(msg)
                is ServerMessage.TimeUpdate -> Id.TIME_UPDATE to proto.encodeToByteArray(msg)
                is ServerMessage.ShortcutBarUpdate ->
                    Id.SHORTCUT_BAR_UPDATE to proto.encodeToByteArray(msg)
                is ServerMessage.LayoutsSync -> Id.LAYOUTS_SYNC to proto.encodeToByteArray(msg)
                ServerMessage.OpenLayoutEditor -> Id.OPEN_LAYOUT_EDITOR to ByteArray(0)
                ServerMessage.OpenPreferences -> Id.OPEN_PREFERENCES to ByteArray(0)
                ServerMessage.OpenCodex -> Id.OPEN_CODEX to ByteArray(0)
                is ServerMessage.RegistrySync -> Id.REGISTRY_SYNC to proto.encodeToByteArray(msg)
                is ServerMessage.NpcSpawned -> Id.NPC_SPAWNED to proto.encodeToByteArray(msg)
                is ServerMessage.NpcDespawned -> Id.NPC_DESPAWNED to proto.encodeToByteArray(msg)
                is ServerMessage.NpcUpdate -> Id.NPC_UPDATE to proto.encodeToByteArray(msg)
                is ServerMessage.NpcInteractResult ->
                    Id.NPC_INTERACT_RESULT to proto.encodeToByteArray(msg)
                is ServerMessage.PreferencesSync ->
                    Id.PREFERENCES_SYNC to proto.encodeToByteArray(msg)
                is ServerMessage.WeatherUpdate -> Id.WEATHER_UPDATE to proto.encodeToByteArray(msg)
            }
        return byteArrayOf(id.b) + payload
    }

    fun decode(data: ByteArray): ServerMessage {
        val payload = data.copyOfRange(1, data.size)
        return when (data[0].toInt()) {
            0 -> proto.decodeFromByteArray<ServerMessage.Welcome>(payload)
            1 -> proto.decodeFromByteArray<ServerMessage.ShadersUpdate>(payload)
            2 -> proto.decodeFromByteArray<ServerMessage.ChunkData>(payload)
            3 -> proto.decodeFromByteArray<ServerMessage.PlayerUpdate>(payload)
            4 -> proto.decodeFromByteArray<ServerMessage.WorldUpdate>(payload)
            5 -> proto.decodeFromByteArray<ServerMessage.PlayerLeft>(payload)
            6 -> proto.decodeFromByteArray<ServerMessage.BlockBreakProgress>(payload)
            7 -> proto.decodeFromByteArray<ServerMessage.Notification>(payload)
            8 -> proto.decodeFromByteArray<ServerMessage.ChatMessage>(payload)
            9 -> proto.decodeFromByteArray<ServerMessage.ChannelsSync>(payload)
            10 -> proto.decodeFromByteArray<ServerMessage.ItemsSpawned>(payload)
            11 -> proto.decodeFromByteArray<ServerMessage.ItemDespawned>(payload)
            12 -> proto.decodeFromByteArray<ServerMessage.InventoryUpdate>(payload)
            13 -> proto.decodeFromByteArray<ServerMessage.TimeUpdate>(payload)
            14 -> proto.decodeFromByteArray<ServerMessage.ShortcutBarUpdate>(payload)
            15 -> proto.decodeFromByteArray<ServerMessage.LayoutsSync>(payload)
            16 -> ServerMessage.OpenLayoutEditor
            17 -> ServerMessage.OpenPreferences
            18 -> ServerMessage.OpenCodex
            19 -> proto.decodeFromByteArray<ServerMessage.RegistrySync>(payload)
            20 -> proto.decodeFromByteArray<ServerMessage.NpcSpawned>(payload)
            21 -> proto.decodeFromByteArray<ServerMessage.NpcDespawned>(payload)
            22 -> proto.decodeFromByteArray<ServerMessage.NpcUpdate>(payload)
            23 -> proto.decodeFromByteArray<ServerMessage.NpcInteractResult>(payload)
            24 -> proto.decodeFromByteArray<ServerMessage.PreferencesSync>(payload)
            25 -> proto.decodeFromByteArray<ServerMessage.WeatherUpdate>(payload)
            else -> throw IllegalArgumentException("Unknown ServerMessage type id: ${data[0]}")
        }
    }
}

object ClientMessageCodec {
    private val proto = ProtoBuf

    fun encode(msg: ClientMessage): ByteArray {
        val (id, payload) =
            when (msg) {
                is ClientMessage.Connect -> 0.toByte() to proto.encodeToByteArray(msg)
                is ClientMessage.MoveIntent -> 1.toByte() to proto.encodeToByteArray(msg)
                is ClientMessage.ChunkUnload -> 2.toByte() to proto.encodeToByteArray(msg)
                is ClientMessage.BlockBreakStart -> 3.toByte() to proto.encodeToByteArray(msg)
                ClientMessage.BlockBreakStop -> 4.toByte() to ByteArray(0)
                is ClientMessage.Command -> 5.toByte() to proto.encodeToByteArray(msg)
                is ClientMessage.BlockPlace -> 6.toByte() to proto.encodeToByteArray(msg)
                is ClientMessage.ShortcutBarSet -> 7.toByte() to proto.encodeToByteArray(msg)
                is ClientMessage.LayoutUpdate -> 8.toByte() to proto.encodeToByteArray(msg)
                is ClientMessage.Disconnect -> 9.toByte() to proto.encodeToByteArray(msg)
                is ClientMessage.NpcInteract -> 10.toByte() to proto.encodeToByteArray(msg)
                is ClientMessage.ChatSend -> 11.toByte() to proto.encodeToByteArray(msg)
                is ClientMessage.PreferencesUpdate -> 12.toByte() to proto.encodeToByteArray(msg)
                is ClientMessage.ViewModeUpdate -> 13.toByte() to proto.encodeToByteArray(msg)
            }
        return byteArrayOf(id) + payload
    }

    fun decode(data: ByteArray): ClientMessage {
        val payload = data.copyOfRange(1, data.size)
        return when (data[0].toInt()) {
            0 -> proto.decodeFromByteArray<ClientMessage.Connect>(payload)
            1 -> proto.decodeFromByteArray<ClientMessage.MoveIntent>(payload)
            2 -> proto.decodeFromByteArray<ClientMessage.ChunkUnload>(payload)
            3 -> proto.decodeFromByteArray<ClientMessage.BlockBreakStart>(payload)
            4 -> ClientMessage.BlockBreakStop
            5 -> proto.decodeFromByteArray<ClientMessage.Command>(payload)
            6 -> proto.decodeFromByteArray<ClientMessage.BlockPlace>(payload)
            7 -> proto.decodeFromByteArray<ClientMessage.ShortcutBarSet>(payload)
            8 -> proto.decodeFromByteArray<ClientMessage.LayoutUpdate>(payload)
            9 -> proto.decodeFromByteArray<ClientMessage.Disconnect>(payload)
            10 -> proto.decodeFromByteArray<ClientMessage.NpcInteract>(payload)
            11 -> proto.decodeFromByteArray<ClientMessage.ChatSend>(payload)
            12 -> proto.decodeFromByteArray<ClientMessage.PreferencesUpdate>(payload)
            13 -> proto.decodeFromByteArray<ClientMessage.ViewModeUpdate>(payload)
            else -> throw IllegalArgumentException("Unknown ClientMessage type id: ${data[0]}")
        }
    }
}
