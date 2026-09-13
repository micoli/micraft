package org.micoli.micraft.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals

class NpcTypeLabelTest {

    @Test
    fun humanizeNpcType_replacesUnderscoresAndCapitalizes() {
        assertEquals("Wolf man", humanizeNpcType("wolf_man"))
        assertEquals("Hermit man", humanizeNpcType("HERMIT_MAN"))
        assertEquals("Seller", humanizeNpcType("seller"))
    }
}
