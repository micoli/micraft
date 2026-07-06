package org.micoli.micraft

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.world.WeatherConfig

class ConfigRegistryTest {

    @AfterTest
    fun resetGlobalGameConstants() {
        GRAVITY = -20f
        SAVE_INTERVAL_TICKS = (30_000L / TICK_MS).toInt()
    }

    @Test
    fun register_thenGet_returnsCurrentValue() {
        val registry = ConfigRegistry()
        var value = "initial"
        registry.register("test:key", get = { value })
        assertEquals("initial", registry.get("test:key"))
        value = "updated"
        assertEquals("updated", registry.get("test:key"))
    }

    @Test
    fun get_unknownKey_returnsNull() {
        val registry = ConfigRegistry()
        assertEquals(null, registry.get("nope"))
    }

    @Test
    fun has_reflectsRegisteredKeys() {
        val registry = ConfigRegistry()
        registry.register("test:key", get = { "v" })
        assertTrue(registry.has("test:key"))
        assertFalse(registry.has("other:key"))
    }

    @Test
    fun isReadOnly_trueWhenNoSetter() {
        val registry = ConfigRegistry()
        registry.register("readonly:key", get = { "v" })
        assertTrue(registry.isReadOnly("readonly:key"))
    }

    @Test
    fun isReadOnly_falseWhenSetterProvided() {
        val registry = ConfigRegistry()
        registry.register("writable:key", get = { "v" }, set = { true })
        assertFalse(registry.isReadOnly("writable:key"))
    }

    @Test
    fun set_withoutSetter_returnsFalse() {
        val registry = ConfigRegistry()
        registry.register("readonly:key", get = { "v" })
        assertFalse(registry.set("readonly:key", "new"))
    }

    @Test
    fun set_unknownKey_returnsFalse() {
        val registry = ConfigRegistry()
        assertFalse(registry.set("nope", "new"))
    }

    @Test
    fun set_withSetter_invokesSetterAndReturnsItsResult() {
        val registry = ConfigRegistry()
        var stored = ""
        registry.register(
            "test:key",
            get = { stored },
            set = { v ->
                stored = v
                true
            })
        assertTrue(registry.set("test:key", "hello"))
        assertEquals("hello", stored)
    }

    @Test
    fun keys_returnsAllRegisteredKeysInOrder() {
        val registry = ConfigRegistry()
        registry.register("a", get = { "" })
        registry.register("b", get = { "" })
        assertEquals(listOf("a", "b"), registry.keys())
    }

    @Test
    fun buildConfigRegistry_gravityRoundTrips() {
        val registry = buildConfigRegistry(WeatherConfig())
        assertTrue(registry.has("game:gravity"))
        assertTrue(registry.set("game:gravity", "-15.5"))
        assertEquals("-15.5", registry.get("game:gravity"))
    }

    @Test
    fun buildConfigRegistry_gravityRejectsNonNumeric() {
        val registry = buildConfigRegistry(WeatherConfig())
        assertFalse(registry.set("game:gravity", "not-a-number"))
    }

    @Test
    fun buildConfigRegistry_tickMsIsReadOnly() {
        val registry = buildConfigRegistry(WeatherConfig())
        assertTrue(registry.isReadOnly("game:tickMs"))
    }

    @Test
    fun buildConfigRegistry_saveIntervalRejectsNonPositive() {
        val registry = buildConfigRegistry(WeatherConfig())
        assertFalse(registry.set("game:saveIntervalSeconds", "0"))
        assertFalse(registry.set("game:saveIntervalSeconds", "-5"))
    }

    @Test
    fun buildConfigRegistry_weatherEnabledParsesBooleanVariants() {
        val registry = buildConfigRegistry(WeatherConfig())
        assertTrue(registry.set("weather:enabled", "false"))
        assertEquals("false", registry.get("weather:enabled"))
        assertTrue(registry.set("weather:enabled", "yes"))
        assertEquals("true", registry.get("weather:enabled"))
        assertFalse(registry.set("weather:enabled", "maybe"))
    }
}
