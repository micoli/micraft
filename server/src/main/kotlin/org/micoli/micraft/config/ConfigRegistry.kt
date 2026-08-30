package org.micoli.micraft.config

import org.micoli.micraft.game.FLY_VERTICAL_SPEED
import org.micoli.micraft.game.GRAVITY
import org.micoli.micraft.game.JUMP_SPEED
import org.micoli.micraft.game.MAX_INTERACTION_DISTANCE
import org.micoli.micraft.game.SAVE_INTERVAL_TICKS
import org.micoli.micraft.game.SPAWN_X
import org.micoli.micraft.game.SPAWN_Y
import org.micoli.micraft.game.SPAWN_Z
import org.micoli.micraft.game.TICKS_PER_DAY
import org.micoli.micraft.game.TICK_MS
import org.micoli.micraft.game.TIME_BROADCAST_TICKS
import org.micoli.micraft.game.world.weather.WeatherConfig

class ConfigRegistry {
    private class Entry(
        val get: () -> String,
        val set: ((String) -> Boolean)?,
    )

    private val entries = linkedMapOf<String, Entry>()

    fun register(key: String, get: () -> String, set: ((String) -> Boolean)? = null) {
        entries[key] = Entry(get, set)
    }

    fun keys(): List<String> = entries.keys.toList()

    fun get(key: String): String? = entries[key]?.get?.invoke()

    fun set(key: String, value: String): Boolean = entries[key]?.set?.invoke(value) ?: false

    fun has(key: String): Boolean = entries.containsKey(key)

    fun isReadOnly(key: String): Boolean = entries[key]?.set == null

    companion object {
        fun parseBool(v: String): Boolean? =
            when (v.lowercase()) {
                "true",
                "1",
                "yes" -> true

                "false",
                "0",
                "no" -> false

                else -> null
            }

        fun buildConfigRegistry(weatherConfig: WeatherConfig): ConfigRegistry =
            ConfigRegistry().apply {
                register("game:gravity", get = { GRAVITY.toString() }) { v ->
                    v.toFloatOrNull()?.let {
                        GRAVITY = it
                        true
                    } ?: false
                }
                register("game:jumpSpeed", get = { JUMP_SPEED.toString() }) { v ->
                    v.toFloatOrNull()?.let {
                        JUMP_SPEED = it
                        true
                    } ?: false
                }
                register("game:flyVerticalSpeed", get = { FLY_VERTICAL_SPEED.toString() }) { v ->
                    v.toFloatOrNull()?.let {
                        FLY_VERTICAL_SPEED = it
                        true
                    } ?: false
                }
                register("game:tickMs", get = { TICK_MS.toString() })
                register(
                    "game:saveIntervalSeconds",
                    get = { (SAVE_INTERVAL_TICKS * TICK_MS / 1000).toString() }) { v ->
                        v.toIntOrNull()
                            ?.takeIf { it > 0 }
                            ?.let {
                                SAVE_INTERVAL_TICKS = (it * 1000L / TICK_MS).toInt()
                                true
                            } ?: false
                    }
                register("game:spawnX", get = { SPAWN_X.toString() }) { v ->
                    v.toFloatOrNull()?.let {
                        SPAWN_X = it
                        true
                    } ?: false
                }
                register("game:spawnY", get = { SPAWN_Y.toString() }) { v ->
                    v.toFloatOrNull()?.let {
                        SPAWN_Y = it
                        true
                    } ?: false
                }
                register("game:spawnZ", get = { SPAWN_Z.toString() }) { v ->
                    v.toFloatOrNull()?.let {
                        SPAWN_Z = it
                        true
                    } ?: false
                }
                register("game:ticksPerDay", get = { TICKS_PER_DAY.toString() }) { v ->
                    v.toLongOrNull()
                        ?.takeIf { it > 0 }
                        ?.let {
                            TICKS_PER_DAY = it
                            true
                        } ?: false
                }
                register("game:timeBroadcastTicks", get = { TIME_BROADCAST_TICKS.toString() }) { v
                    ->
                    v.toIntOrNull()
                        ?.takeIf { it > 0 }
                        ?.let {
                            TIME_BROADCAST_TICKS = it
                            true
                        } ?: false
                }
                register(
                    "game:maxInteractionDistance", get = { MAX_INTERACTION_DISTANCE.toString() }) {
                        v ->
                        v.toDoubleOrNull()
                            ?.takeIf { it > 0 }
                            ?.let {
                                MAX_INTERACTION_DISTANCE = it
                                true
                            } ?: false
                    }
                register("weather:enabled", get = { weatherConfig.data.enabled.toString() }) { v ->
                    parseBool(v)?.let { enabled ->
                        weatherConfig.update { cfg -> cfg.copy(enabled = enabled) }
                        true
                    } ?: false
                }
            }
    }
}
