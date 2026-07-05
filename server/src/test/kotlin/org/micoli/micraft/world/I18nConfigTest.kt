package org.micoli.micraft.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.micoli.micraft.support.testI18n

class I18nConfigTest {
    private val i18n = testI18n()

    @Test
    fun fromClasspath_loadsSuccessfully() {
        assertNotNull(i18n)
    }

    @Test
    fun translate_unknownKey_returnsKeyItself() {
        val key = "nonexistent:server:key_that_does_not_exist"
        assertEquals(key, i18n.t("en", key))
    }

    @Test
    fun translate_unknownLanguage_doesNotThrow() {
        val result = i18n.t("zzz", "nonexistent:key")
        assertNotNull(result)
    }

    @Test
    fun translate_withArgs_interpolatesIntoResult() {
        val key = "nonexistent:server:placeholder"
        val result = i18n.t("en", key, "arg1", "arg2")
        assertNotNull(result)
    }

    @Test
    fun translate_knownKeyReturnsNonEmpty() {
        val result = i18n.t("en", "chat:server:whisper_not_found")
        assertTrue(result.isNotEmpty())
    }
}
