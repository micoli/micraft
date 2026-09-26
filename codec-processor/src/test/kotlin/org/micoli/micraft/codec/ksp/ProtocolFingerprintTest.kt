package org.micoli.micraft.codec.ksp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProtocolFingerprintTest {
    private val protocol =
        listOf(
            "S 0 org.micoli.micraft.protocol.ServerMessage.Welcome(playerId:kotlin.String)",
            "C 0 org.micoli.micraft.protocol.ClientMessage.Connect(playerName:kotlin.String)",
            "T org.micoli.micraft.player.Stance[STANDING,SNEAKING]",
        )

    @Test
    fun `is stable and ignores the order lines were collected in`() {
        assertEquals(ProtocolFingerprint.of(protocol), ProtocolFingerprint.of(protocol.reversed()))
        assertEquals(16, ProtocolFingerprint.of(protocol).length)
    }

    @Test
    fun `changes when a message field, an id or a carried type changes`() {
        val base = ProtocolFingerprint.of(protocol)
        val changes =
            listOf(
                protocol.map { it.replace("playerId:kotlin.String", "playerId:kotlin.Int") },
                protocol.map { it.replace("S 0 ", "S 1 ") },
                protocol.map { it.replace("SNEAKING]", "SNEAKING,CRAWLING]") },
                protocol + "S 1 org.micoli.micraft.protocol.ServerMessage.Pong()",
            )

        changes.forEach { assertNotEquals(base, ProtocolFingerprint.of(it), "unchanged for $it") }
    }
}
