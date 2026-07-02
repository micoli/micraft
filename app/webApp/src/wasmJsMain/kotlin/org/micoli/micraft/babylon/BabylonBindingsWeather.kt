@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

fun jsSetWeatherZones(json: String): Unit = js("mc.setWeatherZones(json)")

fun jsUpdateWeather(scene: JsAny, px: Double, py: Double, pz: Double): Unit =
    js("mc.updateWeather(scene, px, py, pz)")
