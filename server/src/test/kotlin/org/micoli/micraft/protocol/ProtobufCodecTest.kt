package org.micoli.micraft.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.WorldItem
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3

class ProtobufCodecTest {
    private fun roundtripServer(msg: ServerMessage): ServerMessage =
        ServerMessageCodec.decode(ServerMessageCodec.encode(msg))

    private fun roundtripClient(msg: ClientMessage): ClientMessage =
        ClientMessageCodec.decode(ClientMessageCodec.encode(msg))

    @Test
    fun `ServerMessage Welcome roundtrip`() {
        val msg = ServerMessage.Welcome("id1", "Alice", Vec3(1f, 64f, 1f))
        assertEquals(msg, roundtripServer(msg))
    }

    @Test
    fun `ServerMessage ChunkData roundtrip preserves wireBlocks`() {
        val wireBlocks = ByteArray(1024) { it.toByte() }
        val msg = ServerMessage.ChunkData(ChunkPos(3, 7), 200, wireBlocks)
        val decoded = roundtripServer(msg) as ServerMessage.ChunkData
        assertEquals(msg.pos, decoded.pos)
        assertEquals(msg.topY, decoded.topY)
        assert(msg.wireBlocks.contentEquals(decoded.wireBlocks))
    }

    @Test
    fun `ServerMessage PlayerUpdate roundtrip`() {
        val state =
            PlayerState(
                id = "p1",
                name = "Bob",
                pos = Vec3(10f, 65f, 20f),
                orientation = Orientation(45f, -10f),
                stance = PlayerStance.STANDING,
            )
        val msg = ServerMessage.PlayerUpdate(state)
        assertEquals(msg, roundtripServer(msg))
    }

    @Test
    fun `ServerMessage WorldUpdate roundtrip`() {
        val msg =
            ServerMessage.WorldUpdate(
                listOf(
                    BlockChange(BlockPos(1, 64, 1), BlockType.STONE),
                    BlockChange(BlockPos(2, 64, 2), BlockType.AIR),
                ))
        assertEquals(msg, roundtripServer(msg))
    }

    @Test
    fun `ServerMessage object types roundtrip`() {
        assertEquals(
            ServerMessage.OpenLayoutEditor, roundtripServer(ServerMessage.OpenLayoutEditor))
        assertEquals(ServerMessage.OpenPreferences, roundtripServer(ServerMessage.OpenPreferences))
        assertEquals(ServerMessage.OpenCodex, roundtripServer(ServerMessage.OpenCodex))
        assertEquals(ServerMessage.OpenCharacter, roundtripServer(ServerMessage.OpenCharacter))
    }

    @Test
    fun `ServerMessage InventoryUpdate roundtrip`() {
        val msg =
            ServerMessage.InventoryUpdate(
                mapOf(ItemType("COBBLESTONE") to 5, ItemType("DIRT") to 10))
        assertEquals(msg, roundtripServer(msg))
    }

    @Test
    fun `ServerMessage ItemsSpawned roundtrip`() {
        val msg =
            ServerMessage.ItemsSpawned(
                listOf(
                    WorldItem("w1", Vec3(1f, 64f, 1f), ItemType("COBBLESTONE"), 3),
                ))
        assertEquals(msg, roundtripServer(msg))
    }

    @Test
    fun `ServerMessage TimeUpdate roundtrip`() {
        val msg = ServerMessage.TimeUpdate(123456L)
        assertEquals(msg, roundtripServer(msg))
    }

    @Test
    fun `ClientMessage Connect roundtrip`() {
        val msg = ClientMessage.Connect("Alice", "alice@example.com", "fr", "token123")
        assertEquals(msg, roundtripClient(msg))
    }

    @Test
    fun `ClientMessage MoveIntent roundtrip`() {
        val msg =
            ClientMessage.MoveIntent(
                dx = 0.5f,
                dz = -0.3f,
                yaw = 180.5f,
                pitch = -10.2f,
                stance = PlayerStance.SNEAKING,
                jump = true,
                dy = 1.0f,
            )
        assertEquals(msg, roundtripClient(msg))
    }

    @Test
    fun `ClientMessage BlockBreakStop roundtrip`() {
        assertEquals(ClientMessage.BlockBreakStop, roundtripClient(ClientMessage.BlockBreakStop))
    }

    @Test
    fun `ClientMessage ChunkUnload roundtrip`() {
        val msg = ClientMessage.ChunkUnload(listOf(ChunkPos(1, 2), ChunkPos(-3, 4)))
        assertEquals(msg, roundtripClient(msg))
    }

    @Test
    fun `ClientMessage Command roundtrip`() {
        val msg = ClientMessage.Command("/tp 0 64 0")
        assertEquals(msg, roundtripClient(msg))
    }

    @Test
    fun `all ServerMessage type ids are unique`() {
        val encoded =
            listOf(
                ServerMessage.Welcome("", "", Vec3(0f, 0f, 0f)),
                ServerMessage.ShadersUpdate(true),
                ServerMessage.ChunkData(ChunkPos(0, 0), 0, ByteArray(0)),
                ServerMessage.PlayerUpdate(
                    PlayerState(
                        id = "",
                        name = "",
                        pos = Vec3(0f, 0f, 0f),
                        orientation = Orientation(0f, 0f),
                        stance = PlayerStance.STANDING)),
                ServerMessage.WorldUpdate(emptyList()),
                ServerMessage.PlayerLeft(""),
                ServerMessage.BlockBreakProgress(BlockPos(0, 0, 0), 0, 0f),
                ServerMessage.Notification(""),
                ServerMessage.ChatMessage("", "", ""),
                ServerMessage.ChannelsSync(emptyList(), emptyList()),
                ServerMessage.ItemsSpawned(emptyList()),
                ServerMessage.ItemDespawned(""),
                ServerMessage.InventoryUpdate(emptyMap()),
                ServerMessage.TimeUpdate(0L),
                ServerMessage.ShortcutBarUpdate(emptyMap()),
                ServerMessage.LayoutsSync(emptyList(), ""),
                ServerMessage.OpenLayoutEditor,
                ServerMessage.OpenPreferences,
                ServerMessage.OpenCodex,
                ServerMessage.RegistrySync(emptyList(), emptyMap()),
                ServerMessage.NpcSpawned(NpcState("", "", "", Vec3(0f, 0f, 0f), 0f)),
                ServerMessage.NpcDespawned(""),
                ServerMessage.NpcUpdate(NpcState("", "", "", Vec3(0f, 0f, 0f), 0f)),
                ServerMessage.NpcInteractResult("", ""),
                ServerMessage.PreferencesSync(
                    emptyList(), emptyList(), emptySet(), true, emptyList()),
                ServerMessage.WeatherUpdate(emptyList()),
            )
        val typeIds = encoded.map { ServerMessageCodec.encode(it)[0] }
        assertEquals(typeIds.size, typeIds.toSet().size, "Duplicate type IDs detected")
    }
}
