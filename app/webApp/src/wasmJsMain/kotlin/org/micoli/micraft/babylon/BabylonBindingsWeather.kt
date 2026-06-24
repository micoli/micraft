@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

fun jsSetWeatherZones(json: String): Unit = js("mcSetWeatherZones(json)")

fun jsUpdateWeather(scene: JsAny, px: Double, py: Double, pz: Double): Unit =
    js("mcUpdateWeather(scene, px, py, pz)")
