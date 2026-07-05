@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.micoli.micraft.protocol

import kotlin.reflect.KClass
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProtoId(val id: Int)

private val proto = ProtoBuf

internal data class CodecEntry<T>(
    val klass: KClass<out Any>,
    val matches: (T) -> Boolean,
    val encode: (T) -> ByteArray,
    val decode: (ByteArray) -> T,
)

@Suppress("UNCHECKED_CAST")
private inline fun <reified T : ServerMessage> serverEntry() =
    CodecEntry<ServerMessage>(
        klass = T::class,
        matches = { it is T },
        encode = { proto.encodeToByteArray(it as T) },
        decode = { proto.decodeFromByteArray<T>(it) },
    )

private fun serverSingleton(instance: ServerMessage) =
    CodecEntry<ServerMessage>(
        klass = instance::class,
        matches = { it === instance },
        encode = { ByteArray(0) },
        decode = { instance },
    )

@Suppress("UNCHECKED_CAST")
private inline fun <reified T : ClientMessage> clientEntry() =
    CodecEntry<ClientMessage>(
        klass = T::class,
        matches = { it is T },
        encode = { proto.encodeToByteArray(it as T) },
        decode = { proto.decodeFromByteArray<T>(it) },
    )

private fun clientSingleton(instance: ClientMessage) =
    CodecEntry<ClientMessage>(
        klass = instance::class,
        matches = { it === instance },
        encode = { ByteArray(0) },
        decode = { instance },
    )

private fun <T> encodeWith(registry: List<CodecEntry<T>>, msg: T): ByteArray {
    val idx = registry.indexOfFirst { it.matches(msg) }
    if (idx == -1) throw IllegalArgumentException("Unknown message type: ${msg!!::class}")
    return byteArrayOf(idx.toByte()) + registry[idx].encode(msg)
}

private fun <T> decodeWith(registry: List<CodecEntry<T>>, data: ByteArray): T {
    val idx = data[0].toInt()
    return registry.getOrNull(idx)?.decode(data.copyOfRange(1, data.size))
        ?: throw IllegalArgumentException("Unknown message type id: $idx")
}

object ServerMessageCodec {
    internal val registry: List<CodecEntry<ServerMessage>> =
        listOf(
            serverEntry<ServerMessage.Welcome>(), // 0
            serverEntry<ServerMessage.ShadersUpdate>(), // 1
            serverEntry<ServerMessage.ChunkData>(), // 2
            serverEntry<ServerMessage.PlayerUpdate>(), // 3
            serverEntry<ServerMessage.WorldUpdate>(), // 4
            serverEntry<ServerMessage.PlayerLeft>(), // 5
            serverEntry<ServerMessage.BlockBreakProgress>(), // 6
            serverEntry<ServerMessage.Notification>(), // 7
            serverEntry<ServerMessage.ChatMessage>(), // 8
            serverEntry<ServerMessage.ChannelsSync>(), // 9
            serverEntry<ServerMessage.ItemsSpawned>(), // 10
            serverEntry<ServerMessage.ItemDespawned>(), // 11
            serverEntry<ServerMessage.InventoryUpdate>(), // 12
            serverEntry<ServerMessage.TimeUpdate>(), // 13
            serverEntry<ServerMessage.ShortcutBarUpdate>(), // 14
            serverEntry<ServerMessage.LayoutsSync>(), // 15
            serverSingleton(ServerMessage.OpenLayoutEditor), // 16
            serverSingleton(ServerMessage.OpenPreferences), // 17
            serverSingleton(ServerMessage.OpenCodex), // 18
            serverEntry<ServerMessage.RegistrySync>(), // 19
            serverEntry<ServerMessage.NpcSpawned>(), // 20
            serverEntry<ServerMessage.NpcDespawned>(), // 21
            serverEntry<ServerMessage.NpcUpdate>(), // 22
            serverEntry<ServerMessage.NpcInteractResult>(), // 23
            serverEntry<ServerMessage.PreferencesSync>(), // 24
            serverEntry<ServerMessage.WeatherUpdate>(), // 25
            serverEntry<ServerMessage.GameConfigSync>(), // 26
            serverSingleton(ServerMessage.ToggleBiomeMap), // 27
            serverSingleton(ServerMessage.OpenCraft), // 28
            serverEntry<ServerMessage.RecipeSync>(), // 29
            serverEntry<ServerMessage.OpenTrade>(), // 30
            serverEntry<ServerMessage.TradeUpdate>(), // 31
            serverEntry<ServerMessage.TradeClosed>(), // 32
            serverSingleton(ServerMessage.CharacterCreationRequired), // 33
            serverEntry<ServerMessage.CharacterSync>(), // 34
            serverEntry<ServerMessage.CombatTargetUpdate>(), // 35
            serverEntry<ServerMessage.HealthUpdate>(), // 36
            serverEntry<ServerMessage.PlayerStatusUpdate>(), // 37
            serverEntry<ServerMessage.StatusEffectUpdate>(), // 38
            serverEntry<ServerMessage.PlayerDowned>(), // 39
            serverEntry<ServerMessage.PlayerRespawned>(), // 40
        )

    fun encode(msg: ServerMessage): ByteArray = encodeWith(registry, msg)

    fun decode(data: ByteArray): ServerMessage = decodeWith(registry, data)
}

object ClientMessageCodec {
    internal val registry: List<CodecEntry<ClientMessage>> =
        listOf(
            clientEntry<ClientMessage.Connect>(), // 0
            clientEntry<ClientMessage.MoveIntent>(), // 1
            clientEntry<ClientMessage.ChunkUnload>(), // 2
            clientEntry<ClientMessage.BlockBreakStart>(), // 3
            clientSingleton(ClientMessage.BlockBreakStop), // 4
            clientEntry<ClientMessage.Command>(), // 5
            clientEntry<ClientMessage.BlockPlace>(), // 6
            clientEntry<ClientMessage.ShortcutBarSet>(), // 7
            clientEntry<ClientMessage.LayoutUpdate>(), // 8
            clientEntry<ClientMessage.Disconnect>(), // 9
            clientEntry<ClientMessage.NpcInteract>(), // 10
            clientEntry<ClientMessage.ChatSend>(), // 11
            clientEntry<ClientMessage.PreferencesUpdate>(), // 12
            clientEntry<ClientMessage.ViewModeUpdate>(), // 13
            clientEntry<ClientMessage.DoCraft>(), // 14
            clientEntry<ClientMessage.SetCombatTarget>(), // 15
            clientEntry<ClientMessage.AttackTarget>(), // 16
        )

    fun encode(msg: ClientMessage): ByteArray = encodeWith(registry, msg)

    fun decode(data: ByteArray): ClientMessage = decodeWith(registry, data)
}
